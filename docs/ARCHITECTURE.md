# flyingjack-cloud 架构文档

## CI/CD 流水线

### 概览

```
git push
  │
  ├─ 服务自身变更（auth-service / third-party-service）
  │     └─▶ 服务 CI（GitHub Actions self-hosted）
  │           ├─ Maven 构建 + 测试
  │           ├─ Docker build & push → 自托管镜像仓库
  │           └─ 更新 k8s-gitops image tag → ArgoCD 同步 → K8s
  │
  └─ common-lib 变更
        └─▶ common-lib CI（GitHub Actions self-hosted）
              ├─ Maven 构建验证
              └─ repository_dispatch → auth-service CI
                                     → third-party-service CI
                                           └─▶（同上，重新构建 + 推送 + 更新 gitops）
```

### 触发方式

| 触发来源 | 触发方式 | 说明 |
|---|---|---|
| 服务代码变更 | `push` 到 `develop` / `main` | 正常 CI 流程 |
| common-lib 变更 | `repository_dispatch` | common-lib CI 完成后自动派发 |
| 手动重建 | `workflow_dispatch` | GitHub Actions 页面手动触发 |

### Image Tag 规则

| 触发来源 | Tag 格式 | 示例 |
|---|---|---|
| 服务自身 push | `{profile}-{service-sha}` | `beta-3fa9fee` |
| common-lib dispatch | `{profile}-cl{commonlib-sha}` | `beta-cl2d53561` |

`cl` 前缀表示该构建由 common-lib 变更触发。每次 common-lib 有新提交，SHA 不同，tag 必然不同，gitops yaml 必然产生 diff，ArgoCD 必然触发同步。

### common-lib 变更传播链路

```
common-lib push develop
  │
  ▼
common-lib CI (.github/workflows/ci.yml)
  ├─ Maven install common-lib
  └─ curl POST /repos/flyingjack-cloud/auth-service/dispatches
     curl POST /repos/flyingjack-cloud/third-party-service/dispatches
       payload: { ref: "develop", common_lib_sha: "2d53561" }
  │
  ▼
auth-service CI / third-party-service CI
  ├─ checkout 服务代码（ref: develop）
  ├─ checkout common-lib（ref: develop，拉取最新）
  ├─ Maven build
  ├─ Docker build & push
  │    tag: beta-cl2d53561
  └─ 更新 gitops yaml image tag
       旧值: beta-cl17ac3aa  →  新值: beta-cl2d53561
       git diff 有变化 → git push → ArgoCD 自动同步
```

### 分支与环境对应

| 分支 | Maven Profile | K8s 环境 | Image Tag 前缀 |
|---|---|---|---|
| `develop` | `beta` | `flyingjack-beta` | `beta-` |
| `main` | `prod` | `flyingjack-prod` | `prod-` |

### GitOps 仓库

K8s 部署清单存放在独立 GitOps 仓库（Gitee：`zuminli/k8s-gitops`，GitHub 备用：`flyingjack-cloud/k8s-gitops`）。CI 通过 `sed` 更新 `overlays/{profile}/deployment-patch.yaml` 中的 image 字段，ArgoCD 监听该仓库变更后自动同步到集群。

> 当前 GitOps 仓库使用 Gitee（ArgoCD 从国内访问 GitHub 不稳定）。切回 GitHub 时，取消注释各服务 CI 中的 "GitHub（备用）" 步骤并注释 Gitee 步骤。

---

## 网络与路由

### 生产/Beta 入口

所有外部流量通过 Istio IngressGateway（`auth.flyingjack.top`）进入，由 VirtualService 按路径前缀分发：

| 路径前缀 | 目标服务 | 路径改写 |
|---|---|---|
| `/oauth2/` | auth-service | 无 |
| `/.well-known/` | auth-service | 无 |
| `/api/` | auth-service | 去除 `/api` 前缀 |
| `/**` | frontend | 无（兜底） |

业务接口从客户端角度统一走 `/api/` 前缀，OAuth2 标准端点直接访问。

### 客户端 IP 获取

在 Istio 服务网格中，Envoy sidecar 会将流量来源标记为 IngressGateway 的 pod IP（`10.42.x.x`，k3s pod CIDR），导致 `X-Forwarded-For` 被污染。

**解决方案：** `HttpTools.getClientIp()` 优先读取 `X-Envoy-External-Address` header。该 header 由 Istio IngressGateway 在入口处写入真实客户端 IP，经过任意数量的 sidecar 转发均不会被覆盖。

```
优先级：X-Envoy-External-Address → X-Forwarded-For → Proxy-Client-IP → remoteAddr
```

**Feign 内部调用：** auth-service 的 `FeignConfig.ipForwardInterceptor()` 在每个 Feign 请求出口处将真实客户端 IP 注入 `X-Forwarded-For`，确保 third-party-service 等下游服务能正确获取原始客户端 IP。

---

## 服务间通信

所有服务间调用通过 OpenFeign。prod/beta 环境通过 K8s ClusterDNS 解析服务地址（如 `http://thirdparty-service:7100`），不使用外部服务注册中心。dev 环境在 `application-dev.yml` 中通过固定 URL 覆盖。

| 调用方 | 被调用方 | 用途 |
|---|---|---|
| auth-service | third-party-service `/captcha/verify` | 登录/注册时校验图片验证码 |
| wms-cashier | auth-service `/internal/users/by-phone` | 按手机号查询用户 ID |

# flyingjack-cloud 提交代码手册

## 仓库结构

`flyingjack-cloud` 是根聚合工程，下面四个目录都是独立的 Git 子模块（各自有自己的远程仓库）：

- `common-lib`
- `auth-service`
- `third-party-service`
- `wms-cashier`

提交代码时要清楚：你改的文件属于哪个仓库，就该在哪个仓库里单独 commit——**不要在根目录一把梭 `git add -A`**，根仓库和各子模块是完全独立的提交历史。

## 分支规则

- 所有开发都在 **`develop`** 分支进行，**禁止直接 commit 到 `main`**。
- `main` 只接受从 `develop` 合并过来的变更（发布时合并）。
- 这条规则对根仓库和全部四个子模块都适用。
- **提交前必须先确认分支**：

  ```bash
  cd <子模块目录>
  git branch --show-current   # 必须是 develop
  ```

## 提交代码触发的完整流程

```
开发者 push
  │
  ├─ push 到某个服务自己的仓库（auth-service / third-party-service）
  │     └─▶ 服务自身 CI（GitHub Actions self-hosted）
  │           ├─ Maven 构建 + 测试
  │           ├─ Docker build & push → 自托管镜像仓库
  │           └─ 更新 k8s-gitops image tag → ArgoCD 同步 → K8s
  │
  └─ push 到 common-lib 仓库
        │
        ├─ develop 分支
        │     └─▶ common-lib CI
        │           ├─ mvn install（只装本地 .m2，不发布）
        │           └─ repository_dispatch（无条件触发）
        │                 └─▶ auth-service / third-party-service CI
        │                       ├─ checkout common-lib develop 最新源码
        │                       ├─ 本地 mvn install 现装现用
        │                       ├─ 重新构建测试 + 打镜像（beta-cl{sha}）
        │                       └─ 更新 gitops → ArgoCD 同步到 beta
        │                       （每次都是最新代码，不看版本号，一定会更新）
        │
        └─ main 分支
              └─▶ common-lib CI
                    ├─ mvn deploy → 发布到 GitHub Packages（版本号取 pom 当前值）
                    ├─ 按版本号打 git tag（v{version}，已存在则跳过）
                    └─ repository_dispatch（无条件触发，跟 develop 一样会发）
                          └─▶ auth-service / third-party-service CI
                                ├─ 跳过 checkout，直接 mvn clean verify
                                ├─ 按自己 pom 里 pin 的版本号从 Packages 解析 common-lib
                                └─ 构建测试 + 打镜像（prod-cl{sha}）+ 更新 gitops → ArgoCD 同步到 prod
                                （CI 一定会被触发；但只有 pin 的版本号被手动 bump 过，
                                 才会真的用上 common-lib 的新内容——没 bump 就是拉旧版本
                                 重新构建一次，内容不变，等于白跑一次）

Renovate（独立于上面这条流水线，定时触发，不依赖 push）
  │
  └─ 每天定时（.github/workflows/renovate.yml，跑在 self-hosted runner 上）
        └─▶ 扫描 auth-service / third-party-service / wms-cashier 的 pom.xml
              └─ 对比 GitHub Packages 上 common-lib 的最新发布版本 vs pom 里 pin 的版本号
                    ├─ 有新版本 → 自动开 PR，把 pom 里的版本号 bump 上去
                    │     ├─ PR 触发该服务正常的 CI（main 分支那套 mvn verify 流程）
                    │     ├─ patch / minor 版本 → CI 通过后自动 merge
                    │     └─ major 版本 → 只开 PR、打 needs-review 标签，等人工合并
                    └─ 没有新版本 → 什么也不做
```

**Renovate 的作用**：上面 main 分支那条流水线里"CI 会自动触发，但内容更不更新看有没有手动 bump 版本号"这一步，本来需要人自己记得去改三个服务的 pom——Renovate 就是把这个"记得去改"自动化：它不跟着 common-lib 的 push 走，是自己按天定时去检查 Packages 上有没有比 pom 里 pin 的版本更新的发布版本，有就自动开 PR 帮你 bump，PR 本身又会触发正常 CI 去验证兼容性，测试过了（patch/minor）就自动合并，测试没过或者是 major 版本就留给人工处理。

## 日常提交步骤

1. 进入对应的子模块目录（或根目录），确认当前分支是 `develop`。
2. 只 `git add` 自己本次改动涉及的文件——如果同一个文件里混了别人正在进行的其他改动（常见于 `pom.xml` 这类共享配置文件），用 `git add -p` 按 hunk 挑着暂存，不要整文件一起提交。
3. 按 Conventional Commit 格式写提交信息：`类型(服务名:模块): 描述`，类型限定 `feat/fix/refactor/docs/chore/test/perf`。
4. 仓库根目录下也提供了 `/commit <服务名>` 这个 skill（`.claude/skills/submodule-commit`），可以针对单个子模块自动生成规范化的提交信息——但如果该子模块里还混着无关的未提交改动，仍然要先手动挑选暂存范围，再执行提交，不要直接全量 `git add .`。
5. 各子模块单独 `git push`；根仓库单独 `git push`。

## common-lib 版本号规则（重要，容易漏改）

`common-lib` 与三个服务（`auth-service` / `third-party-service` / `wms-cashier`）各自维护**独立的语义化版本号**，不再由根 `pom.xml` 统一管理：

- `common-lib/pom.xml` 自己声明版本，如 `1.0.0`。
- 三个服务在自己 `pom.xml` 里的 `common-lib` 依赖上显式 `<version>` pin 一个具体版本号，必须和 common-lib 当时的版本完全一致。

**common-lib 已发布到 GitHub Packages**（`https://maven.pkg.github.com/flyingjack-cloud/common-lib`），三个服务的 `pom.xml` 都配了对应的 `<repositories>` 指向这个地址。develop 和 main 分支的解析方式不一样：

| 分支 | common-lib 解析方式 |
|---|---|
| `develop` | CI 里 checkout common-lib develop 分支最新 HEAD，本地 `mvn install` 现装现用，不看版本号，也不发布 |
| `main` | common-lib 的 CI 会 `mvn deploy` 把 pom 里当前版本号真正发布到 GitHub Packages；消费方按自己 pom 里 pin 的版本号，直接从 Packages 解析，不再 checkout common-lib 源码 |

也就是说：

- 在 `develop` 上迭代时，不需要手动对齐版本号，CI 会自动拿 common-lib 最新代码本地现装。
- 但凡涉及往 `main` 发布（或者你想让某个服务真正锁定某个 common-lib 版本），**必须先手动把该服务 pom 里的 `common-lib` 依赖版本改成目标版本号**，否则 main 构建会去 Packages 拉一个还没发布过的版本而失败，或者悄悄继续用旧版本、看起来"发布了"但内容没变。
- common-lib 发布新版本的标准动作：改 `common-lib/pom.xml` 里的 `<version>`，合并到 `main` 后 CI 会自动打 tag（`v{version}`，已存在则跳过）并 `mvn deploy` 到 GitHub Packages。发布完成后，再手动去逐个更新下游服务 pom 里 pin 的版本号，提交、推送。

这一步已经接了 Renovate 自动化（见上面"提交代码触发的完整流程"里的说明）——common-lib 发布新版本后不需要再手动逐个改下游 pom，Renovate 每天会自动检测并开 PR。

### 本地开发者单独构建某个服务

如果只想单独 clone 某一个服务仓库（不带完整 root + submodules 的 reactor）来构建，Maven 会按 `reactor → 本地 .m2 缓存 → <repositories> 里的远程仓库` 这个优先级解析 `common-lib`。带着完整 reactor 构建时完全不受影响；单独构建某个服务、且本地没有对应版本缓存时，需要在自己的 `~/.m2/settings.xml` 里配置一个有 `read:packages` 权限的 GitHub 个人 PAT 作为凭证：

```xml
<servers>
    <server>
        <id>github</id>
        <username>你的GitHub用户名</username>
        <password>你的PAT（read:packages）</password>
    </server>
</servers>
```

注意：只有 `main` 上发布过的版本才能这样拉取——`develop` 上的改动不发布，若 pin 的版本还没被 deploy 过，单独构建会直接报错找不到（这也是预期行为，`develop` 阶段本来就该带着 reactor 一起构建）。

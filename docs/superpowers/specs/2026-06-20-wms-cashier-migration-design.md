# wms-cashier 微服务改造设计文档

**日期：** 2026-06-20
**作者：** Zumin Li
**状态：** 已确认，待实施

---

## 背景

`wms_backend` 是原来的单体 WMS 应用，包含认证、用户管理、商品、订单、分类、店铺组织等全部功能。现在认证已迁移至 `auth-service`（OAuth2），第三方接口迁移至 `third-party-service`，本文档描述将单体剩余 WMS 业务逻辑迁移至 `wms-cashier` 微服务的完整设计。

---

## Section 1：职责边界与迁移范围

### 迁移到 wms-cashier

| 单体来源 | 说明 |
|---|---|
| `CategoryController/Service/DAO` | 分类 CRUD，按 group 隔离 |
| `MerchandiseController/Service/DAO` | 商品 CRUD、搜索、统计 |
| `OrderController/Service/DAO` | 订单创建/查询/退单 |
| `GroupController/Service/DAO` | 店铺组织、员工加入申请、权限审批 |
| `NoticeController/Service/DAO` | 公告 |
| `ProfileController`（role/permission/nickname 部分） | `GET /profile/role`、`GET /profile/permissions`、`PUT /profile/nickname`（nickname 存于 wms_user_profile） |
| `AuthorityService/DAO` | WMS 权限管理 |

### 从单体删除（已由其他服务覆盖）

| 单体来源 | 现在由谁负责 |
|---|---|
| `SignUpController`（注册） | auth-service `/account/register` |
| `UserController`（重置密码、发验证码、查用户名/手机/邮箱） | auth-service + third-party-service |
| JWT 自签发、自定义 filter 链 | auth-service OAuth2 替代 |
| `MailService`、`SmsService` | third-party-service |
| `ProfileController.getProfile()`（username/phone/email） | auth-service `/account/profile` |

### auth-service 需新增内部接口

`GET /internal/users/by-phone?phone={phone}` → 返回 `{ userId: Long }`

供 wms-cashier 通过 Feign 调用，用于"按店主手机号发起加入申请"场景。该接口仅供内部调用，生产环境通过 Istio `AuthorizationPolicy` 限制来源为 wms-cashier。

---

## Section 2：数据库 Schema

wms-cashier 拥有独立 PostgreSQL 实例。

### 建表 DDL

```sql
-- WMS 用户身份映射（user_id 来自 JWT sub，Snowflake BIGINT）
CREATE TABLE wms_user_profile (
    user_id     BIGINT PRIMARY KEY,
    group_id    INT NOT NULL DEFAULT 0,
    role        VARCHAR(32) NOT NULL DEFAULT 'ROLE_DEFAULT',
    nickname    VARCHAR(64)
);

-- WMS 细粒度权限（SHOPPING / INVENTORY / STATISTICS）
CREATE TABLE wms_authority (
    user_id     BIGINT NOT NULL,
    authority   VARCHAR(64) NOT NULL,
    PRIMARY KEY (user_id, authority)
);

-- 店铺组
CREATE TABLE wms_group (
    id          SERIAL PRIMARY KEY,
    store_name  VARCHAR(128) NOT NULL,
    address     VARCHAR(256),
    contact     VARCHAR(32),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 加入申请
CREATE TABLE wms_join_request (
    user_id      BIGINT NOT NULL,
    group_id     INT NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id)
);

-- 商品分类（按组隔离）
CREATE TABLE wms_category (
    id          SERIAL PRIMARY KEY,
    group_id    INT NOT NULL DEFAULT 0,
    parent_id   INT NOT NULL DEFAULT 0,
    name        VARCHAR(64) NOT NULL
);

-- 商品
CREATE TABLE wms_merchandise (
    id          SERIAL PRIMARY KEY,
    group_id    INT NOT NULL,
    cate_id     INT NOT NULL,
    cost        NUMERIC(12,2) NOT NULL,
    price       NUMERIC(12,2) NOT NULL,
    imei        VARCHAR(64),
    sold        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL
);

-- 订单
CREATE TABLE wms_order (
    id            SERIAL PRIMARY KEY,
    group_id      INT NOT NULL,
    me_id         INT NOT NULL,
    selling_price NUMERIC(12,2) NOT NULL,
    selling_time  TIMESTAMPTZ NOT NULL,
    remark        VARCHAR(256),
    is_returned   BOOLEAN NOT NULL DEFAULT FALSE
);

-- 公告
CREATE TABLE wms_notice (
    id          SERIAL PRIMARY KEY,
    group_id    INT NOT NULL DEFAULT 0,
    type        VARCHAR(32) NOT NULL,
    content     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### 数据迁移策略

迁移分两步执行：

**Step 1：用户 ID 映射（auth-service 侧）**

单体用 `INT` 自增 userId，auth-service 用 Snowflake `BIGINT`。迁移时将旧用户批量导入 auth-service，并记录映射关系：

```sql
-- 临时映射表（auth-service DB）
CREATE TABLE user_id_mapping (
    old_int_id   INT PRIMARY KEY,
    new_bigint_id BIGINT NOT NULL
);
```

**Step 2：wms-cashier 数据迁移脚本**（按序执行）

1. 迁 `wms_group`：从旧 `group` 表直接复制，id 不变
2. 迁 `wms_user_profile`：旧 `user.group_id` + `authority` 表中的 role，userId 替换为新 BIGINT
3. 迁 `wms_authority`：permission 记录，userId 替换为新 BIGINT
4. 迁 `wms_category`：直接复制，group_id 不变
5. 迁 `wms_merchandise`：直接复制（无 user_id FK）
6. 迁 `wms_order`：直接复制（无 user_id FK）
7. 迁 `wms_join_request`：userId 替换为新 BIGINT
8. 迁 `wms_notice`：直接复制

category、merchandise、order 不存 user_id，只存 group_id，无需 ID 替换，迁移最简单。

---

## Section 3：身份识别与 WMS 授权

### 授权流程

```
请求携带 Bearer JWT
        ↓
ResourceServerConfig 验证 JWT 签名（JWKS from auth-service）
        ↓
CustomJwtAuthenticationConverter 提取 sub（= auth-service userId BIGINT）
        ↓
查询 wms_user_profile → 得到 role（如 ROLE_OWNER）
查询 wms_authority    → 得到 permissions（如 PERMISSION:shopping）
        ↓
构建 JwtAuthenticationToken，authorities = role + permissions
        ↓
Spring Security @PreAuthorize / RoleHierarchy 正常工作
```

### 核心组件

**`CustomJwtAuthenticationConverter`**
- 实现 `Converter<Jwt, AbstractAuthenticationToken>`
- 从 `jwt.getSubject()` 获取 userId（Long）
- 查 `wms_user_profile` + `wms_authority` 加载 WMS 角色和权限
- 若无对应行（新用户首次访问），惰性插入默认行（`group_id=0, role=ROLE_DEFAULT`）
- 返回含完整 authorities 的 `JwtAuthenticationToken`

**`ResourceServerConfig`**
```java
http.oauth2ResourceServer(oauth2 -> oauth2
    .jwt(jwt -> jwt
        .jwkSetUri("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
        .jwtAuthenticationConverter(customJwtAuthenticationConverter)
    ));
```

**RoleHierarchy**（从单体迁移）
```
ROLE_ADMIN > ROLE_OWNER
ROLE_OWNER > ROLE_STAFF
ROLE_OWNER > PERMISSION:shopping
ROLE_OWNER > PERMISSION:inventory
ROLE_OWNER > PERMISSION:statistic
```

**`@EnableMethodSecurity`** 开启，Service 层 `@PreAuthorize` 注解保持不变。

**`SecurityUtils`**（替代单体 `AbstractAuthenticationService`）
```java
public static Long currentUserId() {
    Jwt jwt = (Jwt) SecurityContextHolder.getContext()
                     .getAuthentication().getPrincipal();
    return Long.parseLong(jwt.getSubject());
}
```

---

## Section 4：模块结构与依赖

### 包结构

```
top.flyingjack.cashier
├── CashierApplication.java               # @EnableGlobalException @EnableGlobalI18n
├── config/
│   ├── ResourceServerConfig.java         # 升级：converter + role hierarchy + method security
│   ├── CustomJwtAuthenticationConverter.java
│   └── AppConfig.java                    # MyBatis、事务
├── feign/
│   └── AuthServiceClient.java            # K8s 服务发现，按手机查 userId
├── entity/
│   ├── WmsUserProfile.java
│   ├── Group.java
│   ├── Category.java
│   ├── Merchandise.java
│   ├── MeCount.java                      # 商品统计 DTO
│   ├── Order.java
│   ├── Notice.java
│   └── SystemAuthority.java              # WMS 角色/权限枚举
├── mapper/
│   ├── WmsUserProfileMapper.java
│   ├── GroupMapper.java
│   ├── CategoryMapper.java
│   ├── MerchandiseMapper.java
│   ├── OrderMapper.java
│   ├── NoticeMapper.java
│   └── AuthorityMapper.java
├── service/
│   ├── CategoryService.java
│   ├── MerchandiseService.java
│   ├── OrderService.java
│   ├── GroupService.java
│   ├── NoticeService.java
│   ├── ProfileService.java
│   └── AuthorityService.java
├── controller/
│   ├── CategoryController.java
│   ├── MerchandiseController.java
│   ├── OrderController.java
│   ├── GroupController.java
│   ├── NoticeController.java
│   └── ProfileController.java
└── tool/
    └── SecurityUtils.java
```

### pom.xml 新增依赖

```xml
<!-- MyBatis -->
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
    <version>3.0.3</version>
</dependency>

<!-- PostgreSQL -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- 测试 -->
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter-test</artifactId>
    <version>3.0.3</version>
    <scope>test</scope>
</dependency>
```

### Feign Client

使用 K8s 服务发现，不指定 `url`：

```java
@FeignClient(name = "auth-service")
public interface AuthServiceClient {
    @GetMapping("/internal/users/by-phone")
    ApiRes<Long> getUserIdByPhone(@RequestParam("phone") String phone);
}
```

dev 环境在 `application-dev.yml` 覆盖地址：

```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          auth-service:
            url: http://localhost:9001
```

### application.yml 配置

所有值由 K8s env 注入，不使用 spring-cloud-kubernetes-config：

```yaml
server:
  port: 8081

spring:
  application:
    name: wms-cashier
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${AUTH_ISSUER_URI:http://localhost:9001}
```

---

## Section 5：错误处理与测试

### 错误处理

- `CashierApplication` 加 `@EnableGlobalException` + `@EnableGlobalI18n`
- 业务异常抛 `BusinessException`，错误码在 `SysErrorCode` 枚举定义，同步添加 `messages.properties` / `messages_en_US.properties` / `messages_zh_CN.properties` i18n 条目
- `CustomJwtAuthenticationConverter`：首次遇到无 `wms_user_profile` 的用户时，惰性插入默认行（`group_id=0, role=ROLE_DEFAULT`），不抛异常
- 本期不引入 `@EnableGlobalCache`（无明确缓存场景）

### 日期/时间

- 所有实体字段用 `Instant`，替换单体 `Date`
- 接口参数 `long` epoch ms → Service 层转 `Instant.ofEpochMilli()`
- DB 存 `TIMESTAMPTZ`（UTC+0）

### 测试

**单元测试**
- Service 层：Mockito mock Mapper，`AuthServiceClient` 用 `@MockBean`
- 覆盖核心业务逻辑：权限校验、状态流转、数据校验

**集成测试**
- 继承 `BaseContainerTest`（PostgreSQL TestContainers，`application-test` profile）
- 覆盖场景：创建店铺、员工加入申请与审批、商品 CRUD、订单创建与退单、OWNER/STAFF/DEFAULT 角色权限隔离
- 无需 Redis TestContainers（本期不加缓存）

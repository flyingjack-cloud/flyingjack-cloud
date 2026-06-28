# WMS 数据迁移手册

从单体 `wms_backend` → 微服务 `auth-service` + `wms-cashier`

---

## 一、背景与目标

`wms_backend` 是原 Spring MVC 单体应用，用户认证和 WMS 业务数据共用一个 PostgreSQL 数据库（`wms`）。微服务化后拆分为两个独立服务和数据库：

| 旧 | 新 |
|---|---|
| `wms_backend` 认证模块 | `auth-service`（数据库 `auth`） |
| `wms_backend` WMS 业务模块 | `wms-cashier`（数据库 `wms_cashier`） |

**用户 ID 类型变更：** 旧 `users.id` 是 `SERIAL`（INT），auth-service 的 `auth_users.id` 是 `BIGINT`（Snowflake）。  
**迁移策略：直接将旧 INT id 强转 BIGINT 写入 auth_users，跳过 Snowflake 生成。**

这是可行的，因为：
- 旧 serial ID 范围：1, 2, 3 ... 最多数千
- Snowflake ID 最小值：约 `10^15`（时间戳编码，e.g. `1799929780000000000`）
- 两个范围**永不重叠**，新注册用户的 Snowflake ID 不会与迁移用户的小整数 ID 冲突

---

## 二、表结构对照

### 整体映射

| 旧表（wms_backend/wms） | 新表（auth） | 新表（wms_cashier） |
|---|---|---|
| `users` + `users_detail` | `auth_users` | `wms_user_profile`（WMS 角色） |
| `authorities`（ROLE_*） | `user_role` | `wms_user_profile.role` |
| `authorities`（PERMISSION:*） | — | `wms_authority` |
| `groups` | — | `wms_group` |
| `group_request` | — | `wms_join_request` |
| `category` | — | `wms_category` |
| `merchandise` | — | `wms_merchandise` |
| `orders` | — | `wms_order` |
| `notices` | — | `wms_notice` |
| `receipts` | — | **不迁移** |

### 关键列变更

**users → auth_users（id 直接 CAST，不用 Snowflake）**

| 旧列 | 新列 | 处理方式 |
|---|---|---|
| `id SERIAL`（INT） | `id BIGINT PK` | `old_id::BIGINT`，直接使用 |
| `username VARCHAR(50)` | `username VARCHAR(50)` | 直接复制 |
| `password VARCHAR(500)` | `password VARCHAR(500)` | 直接复制（bcrypt 格式兼容） |
| `enabled BOOLEAN` | `enabled BOOLEAN` | 直接复制 |
| `accountNonExpired` | `account_non_expired` | 列名 camelCase → snake_case |
| `accountNonLocked` | `account_non_locked` | 同上 |
| `credentialsNonExpired` | `credentials_non_expired` | 同上 |
| *(users_detail.email)* | `email` | 从 users_detail JOIN |
| *(users_detail.phone_number)* | `phone` | 从 users_detail JOIN |
| *(无)* | `created_at` | 填 `NOW()` |
| *(无)* | `two_factor_enabled` | 迁移用户默认 `false`（列 DEFAULT） |
| *(无)* | `two_factor_secret` | 迁移用户默认 `NULL` |

**authorities（ROLE_*）→ user_role**

旧 `authorities` 表中 `ROLE_USER` 等为 auth 系统角色。auth-service 有独立的 `auth_roles` 表（id=1:ROLE_ADMIN, 2:ROLE_USER, 3:ROLE_GUEST），迁移时映射角色名到角色 ID。

**注意：** WMS 业务角色（`ROLE_OWNER`、`ROLE_STAFF`、`ROLE_DEFAULT`、`PERMISSION:*`）不迁移到 auth 系统，只迁移到 `wms_cashier`。

**groups → wms_group**

| 旧列 | 新列 | 处理方式 |
|---|---|---|
| `group_id SERIAL PK` | `id SERIAL PK` | 值不变 |
| `store_name VARCHAR(100)` | `store_name VARCHAR(128)` | 直接复制 |
| `address VARCHAR(200)` | `address VARCHAR(256)` | 直接复制 |
| `contact VARCHAR(50)` | `contact VARCHAR(32)` | ⚠️ 有缩容，见注意事项 |
| `create_time TIMESTAMP` | `created_at TIMESTAMPTZ` | 重命名+加时区 |

**users → wms_user_profile（WMS 业务身份，userId 直接 CAST）**

| 旧来源 | 新列 | 处理方式 |
|---|---|---|
| `users.id::BIGINT` | `user_id BIGINT PK` | INT → BIGINT cast |
| `users.group_id` | `group_id INT` | 直接复制 |
| `authorities.authority`（最高 ROLE） | `role VARCHAR` | 取最高角色写入 |
| `users_detail.nickname` | `nickname VARCHAR(64)` | 直接复制 |

**group_request → wms_join_request**

| 旧列 | 新列 | 处理方式 |
|---|---|---|
| `user_id INT` | `user_id BIGINT` | `::BIGINT` cast |
| `group_id INT` | `group_id INT` | 直接复制 |
| *(无)* | `requested_at TIMESTAMPTZ` | 填 `NOW()` |

**merchandise → wms_merchandise**（`own_id` 丢弃）

| 旧列 | 新列 |
|---|---|
| `me_id SERIAL PK` | `id SERIAL PK` |
| `group_id`, `cate_id`, `cost`, `price`, `imei`, `sold` | 同名直接复制 |
| `create_time TIMESTAMP` | `created_at TIMESTAMPTZ` |
| `own_id` | **不迁移** |

**orders → wms_order**（`own_id` 丢弃）

| 旧列 | 新列 |
|---|---|
| `order_id SERIAL PK` | `id SERIAL PK` |
| `group_id`, `me_id`, `selling_price`, `selling_time`, `remark` | 同名直接复制 |
| `returned BOOLEAN` | `is_returned BOOLEAN` |
| `own_id` | **不迁移** |

**notices → wms_notice（⚠️ 旧表无 group_id）**

旧 `notices` 是全局公告，无门店归属。迁移时 `group_id` 统一填 `0`（系统级），业务层对 `group_id=0` 的公告作全局展示。

---

## 三、前置条件

- [ ] 旧库（`wms`）与新库在同一 PostgreSQL 实例，或可通过 `dblink` 互通
- [ ] 新库 `auth` 已建好（`auth-service/src/main/resources/schema.sql` 已执行）
- [ ] 新库 `wms_cashier` 已建好（`wms-cashier/src/main/resources/schema.sql` 已执行）
- [ ] 旧库数据已备份

---

## 四、迁移步骤

### Step 0：备份

```bash
pg_dump -h <host> -U postgres -d wms -F c -f wms_backup_$(date +%Y%m%d).dump
```

回滚时：
```bash
pg_restore -h <host> -U postgres -d wms -F c wms_backup_<date>.dump
```

---

### Step 1：迁移用户到 auth-service

> 在 `auth` 数据库执行。旧库通过 schema 限定符 `wms.` 访问（同实例）。**跨实例场景请使用第九节的 dblink 版本。**

```sql
\c auth

-- 1a. 迁移用户主体（id 直接强转 BIGINT，跳过 Snowflake 生成）
INSERT INTO auth_users (id, username, email, phone, password,
                        account_non_expired, account_non_locked,
                        credentials_non_expired, enabled, created_at)
SELECT
    u.id::BIGINT,
    u.username,
    ud.email,
    ud.phone_number,
    u.password,
    u."accountnonexpired",
    u."accountnonlocked",
    u."credentialsnonexpired",
    u.enabled,
    NOW()
FROM wms.users u
LEFT JOIN wms.users_detail ud ON u.id = ud.user_id
ON CONFLICT (id) DO NOTHING;

-- 1b. 迁移 auth 系统角色关联（仅迁移系统级角色，WMS 角色不在这里）
-- auth_roles 预置：1=ROLE_ADMIN, 2=ROLE_USER, 3=ROLE_GUEST
-- 旧系统所有用户视为普通用户（ROLE_USER）
INSERT INTO user_role (user_id, role_id)
SELECT u.id::BIGINT, 2   -- 2 = ROLE_USER
FROM wms.users u
ON CONFLICT DO NOTHING;

-- 若旧系统有管理员（ROLE_ADMIN），额外插入
INSERT INTO user_role (user_id, role_id)
SELECT a.user_id::BIGINT, 1  -- 1 = ROLE_ADMIN
FROM wms.authorities a
WHERE a.authority = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;
```

**验证：**
```sql
SELECT COUNT(*) AS auth_user_count FROM auth_users;
-- 应等于 SELECT COUNT(*) FROM wms.users;
```

---

### Step 2：迁移 WMS 业务数据到 wms-cashier

> 在 `wms_cashier` 数据库执行。**跨实例场景请使用第九节的 dblink 版本。**

```sql
\c wms_cashier

-- ================================================
-- 2-1. wms_group（排除 group_id=0 的系统占位行）
-- ================================================
INSERT INTO wms_group (id, store_name, address, contact, created_at)
SELECT
    group_id,
    store_name,
    address,
    CASE WHEN length(contact) > 32 THEN left(contact, 32) ELSE contact END,
    create_time AT TIME ZONE 'Asia/Shanghai'   -- ⚠️ 根据旧库实际时区调整，UTC 则直接写 create_time
FROM wms.groups
WHERE group_id > 0
ON CONFLICT (id) DO NOTHING;

-- 重置序列，避免后续 INSERT 主键冲突
SELECT setval('wms_group_id_seq', COALESCE((SELECT MAX(id) FROM wms_group), 1));

-- ================================================
-- 2-2. wms_user_profile（WMS 业务身份）
--      userId = 旧 INT id 直接 CAST BIGINT，无需映射表
--      role 取最高角色：ADMIN > OWNER > STAFF > DEFAULT
-- ================================================
INSERT INTO wms_user_profile (user_id, group_id, role, nickname)
SELECT
    u.id::BIGINT,
    u.group_id,
    CASE
        WHEN EXISTS (SELECT 1 FROM wms.authorities a
                     WHERE a.user_id = u.id AND a.authority = 'ROLE_ADMIN')
            THEN 'ROLE_ADMIN'
        WHEN EXISTS (SELECT 1 FROM wms.authorities a
                     WHERE a.user_id = u.id AND a.authority = 'ROLE_OWNER')
            THEN 'ROLE_OWNER'
        WHEN EXISTS (SELECT 1 FROM wms.authorities a
                     WHERE a.user_id = u.id AND a.authority = 'ROLE_STAFF')
            THEN 'ROLE_STAFF'
        ELSE 'ROLE_DEFAULT'
    END,
    ud.nickname
FROM wms.users u
LEFT JOIN wms.users_detail ud ON u.id = ud.user_id
ON CONFLICT (user_id) DO NOTHING;

-- ================================================
-- 2-3. wms_authority（仅迁移细粒度 PERMISSION:*）
--      userId = 旧 INT 直接 CAST
-- ================================================
INSERT INTO wms_authority (user_id, authority)
SELECT user_id::BIGINT, authority
FROM wms.authorities
WHERE authority LIKE 'PERMISSION:%'
ON CONFLICT DO NOTHING;

-- ================================================
-- 2-4. wms_category
-- ================================================
INSERT INTO wms_category (id, group_id, parent_id, name)
SELECT cate_id, group_id, parent_cate_id, name
FROM wms.category
ON CONFLICT (id) DO NOTHING;

SELECT setval('wms_category_id_seq', COALESCE((SELECT MAX(id) FROM wms_category), 1));

-- ================================================
-- 2-5. wms_merchandise
-- ================================================
INSERT INTO wms_merchandise (id, group_id, cate_id, cost, price, imei, sold, created_at)
SELECT me_id, group_id, cate_id, cost, price, imei, sold,
       create_time AT TIME ZONE 'Asia/Shanghai'
FROM wms.merchandise
ON CONFLICT (id) DO NOTHING;

SELECT setval('wms_merchandise_id_seq', COALESCE((SELECT MAX(id) FROM wms_merchandise), 1));

-- ================================================
-- 2-6. wms_order
-- ================================================
INSERT INTO wms_order (id, group_id, me_id, selling_price, selling_time, remark, is_returned)
SELECT order_id, group_id, me_id, selling_price,
       selling_time AT TIME ZONE 'Asia/Shanghai',
       remark, returned
FROM wms.orders
ON CONFLICT (id) DO NOTHING;

SELECT setval('wms_order_id_seq', COALESCE((SELECT MAX(id) FROM wms_order), 1));

-- ================================================
-- 2-7. wms_join_request（旧表无申请时间，填 NOW()）
-- ================================================
INSERT INTO wms_join_request (user_id, group_id, requested_at)
SELECT user_id::BIGINT, group_id, NOW()
FROM wms.group_request
ON CONFLICT (user_id) DO NOTHING;

-- ================================================
-- 2-8. wms_notice（旧表无 group_id，统一填 0）
-- ================================================
INSERT INTO wms_notice (id, group_id, type, content, created_at)
SELECT id, 0, type, content,
       publish_time AT TIME ZONE 'Asia/Shanghai'
FROM wms.notices
ON CONFLICT (id) DO NOTHING;

SELECT setval('wms_notice_id_seq', COALESCE((SELECT MAX(id) FROM wms_notice), 1));
```

---

## 五、验证

```sql
\c wms_cashier

-- 1. 用户数量一致
SELECT COUNT(*) FROM wms_user_profile;
-- 对比：SELECT COUNT(*) FROM wms.users;

-- 2. 门店数量一致（排除占位行）
SELECT COUNT(*) FROM wms_group;
-- 对比：SELECT COUNT(*) - 1 FROM wms.groups;

-- 3. 分类、商品、订单总数一致
SELECT COUNT(*) FROM wms_category;   -- 对比 wms.category
SELECT COUNT(*) FROM wms_merchandise; -- 对比 wms.merchandise
SELECT COUNT(*) FROM wms_order;       -- 对比 wms.orders

-- 4. OWNER 角色数量一致
SELECT COUNT(*) FROM wms_user_profile WHERE role = 'ROLE_OWNER';
-- 对比：SELECT COUNT(*) FROM wms.authorities WHERE authority = 'ROLE_OWNER';

-- 5. 孤立商品检查（分类不存在，结果应为 0）
SELECT COUNT(*) FROM wms_merchandise m
LEFT JOIN wms_category c ON m.cate_id = c.id
WHERE c.id IS NULL;

-- 6. 孤立订单检查（商品不存在，结果应为 0）
SELECT COUNT(*) FROM wms_order o
LEFT JOIN wms_merchandise m ON o.me_id = m.id
WHERE m.id IS NULL;

-- 7. 序列检查（seq_val 应 >= max_id）
SELECT 'wms_group'       AS tbl, MAX(id) AS max_id FROM wms_group
UNION ALL SELECT 'wms_category',    MAX(id) FROM wms_category
UNION ALL SELECT 'wms_merchandise', MAX(id) FROM wms_merchandise
UNION ALL SELECT 'wms_order',       MAX(id) FROM wms_order
UNION ALL SELECT 'wms_notice',      MAX(id) FROM wms_notice;
```

---

## 六、注意事项

| 问题 | 说明 | 处理方式 |
|---|---|---|
| `contact` 列缩容（50→32） | 旧列更宽 | 迁移 SQL 中用 `left(contact, 32)` 截断；建议迁移前检查：`SELECT * FROM wms.groups WHERE length(contact) > 32` |
| `notices` 无 `group_id` | 旧表是全局公告 | 填 `0`，业务层当全局公告展示 |
| `group_request` 无申请时间 | 旧表无此字段 | 填 `NOW()`，历史申请时间信息丢失 |
| `receipts` 表不迁移 | 新架构无此功能 | 旧库备份保留 |
| `TIMESTAMP` 无时区 | PostgreSQL 默认存本地时间 | 迁移 SQL 中加 `AT TIME ZONE 'Asia/Shanghai'`；若旧库已是 UTC 则直接用列值 |
| 确认旧库时区 | `SHOW timezone;` | 若返回 `UTC` 则去掉 `AT TIME ZONE` 转换；若返回 `Asia/Shanghai` 则保留 |
| `own_id` 列丢弃 | category/merchandise/orders 的创建者不再记录 | 旧库备份保留，新架构按 `group_id` 隔离 |

---

## 七、切换流程

1. **维护窗口开始**：下线旧 `wms_backend`（Nginx 返回 503）
2. 执行 Step 0 备份
3. 执行 Step 1（用户迁移到 auth）+ Step 2（WMS 数据迁移）
4. 执行 Step 5 全部验证 SQL，确认通过
5. 启动 `wms-cashier`，smoke test 关键接口
6. **切换流量**：Istio VirtualService 路由到 `wms-cashier`
7. 观察 30 分钟，确认无异常

**回滚：** Istio 路由回 `wms_backend`，`wms_cashier` 数据库 DROP 后从 Step 2 重新执行（所有 INSERT 有 `ON CONFLICT DO NOTHING`，幂等可重复执行）。

---

## 九、跨实例迁移（dblink 方案）

当旧库（`wms`）与新库**不在同一 PostgreSQL 实例**时，使用 PostgreSQL 原生 `dblink` 扩展代替 schema 限定符访问。本节 SQL 与第四节等价，二选一执行。

### 前置：安装 dblink 扩展

在**目标实例**的两个目标库中分别执行：

```sql
\c auth
CREATE EXTENSION IF NOT EXISTS dblink;

\c wms_cashier
CREATE EXTENSION IF NOT EXISTS dblink;
```

### 连接串配置

```
host=<源库IP> port=5432 dbname=wms user=postgres password=<密码>
```

建议将密码写入 `~/.pgpass`（格式 `host:port:dbname:user:password`），避免密码出现在 SQL 会话历史：

```
<源库IP>:5432:wms:postgres:<密码>
```

配置后连接串可省略 `password` 字段：`'host=<源库IP> port=5432 dbname=wms user=postgres'`

---

### Step 1（dblink 版）：迁移用户到 auth-service

```sql
\c auth

-- 建立命名连接（当前会话有效）
SELECT dblink_connect('wms_src', 'host=<源库IP> port=5432 dbname=wms user=postgres password=<密码>');

-- 1a. 迁移用户主体（id 直接强转 BIGINT，跳过 Snowflake 生成）
INSERT INTO auth_users (id, username, email, phone, password,
                        account_non_expired, account_non_locked,
                        credentials_non_expired, enabled, created_at)
SELECT
    u_id::BIGINT,
    username,
    email,
    phone_number,
    password,
    acct_non_expired,
    acct_non_locked,
    cred_non_expired,
    enabled,
    NOW()
FROM dblink('wms_src',
    'SELECT u.id, u.username, ud.email, ud.phone_number, u.password,
            u."accountnonexpired", u."accountnonlocked", u."credentialsnonexpired", u.enabled
     FROM users u
     LEFT JOIN users_detail ud ON u.id = ud.user_id'
) AS t(u_id INT, username VARCHAR(50), email VARCHAR(100), phone_number VARCHAR(20),
       password VARCHAR(500), acct_non_expired BOOLEAN, acct_non_locked BOOLEAN,
       cred_non_expired BOOLEAN, enabled BOOLEAN)
ON CONFLICT (id) DO NOTHING;

-- 1b. 所有迁移用户默认赋予 ROLE_USER（role_id=2）
INSERT INTO user_role (user_id, role_id)
SELECT u_id::BIGINT, 2
FROM dblink('wms_src', 'SELECT id FROM users') AS t(u_id INT)
ON CONFLICT DO NOTHING;

-- 1c. 旧系统管理员额外赋予 ROLE_ADMIN（role_id=1）
INSERT INTO user_role (user_id, role_id)
SELECT u_id::BIGINT, 1
FROM dblink('wms_src',
    'SELECT user_id FROM authorities WHERE authority = ''ROLE_ADMIN'''
) AS t(u_id INT)
ON CONFLICT DO NOTHING;

SELECT dblink_disconnect('wms_src');
```

**验证：**
```sql
SELECT COUNT(*) AS auth_user_count FROM auth_users;
-- 应等于源库：SELECT COUNT(*) FROM users;
```

---

### Step 2（dblink 版）：迁移 WMS 业务数据到 wms-cashier

```sql
\c wms_cashier

SELECT dblink_connect('wms_src', 'host=<源库IP> port=5432 dbname=wms user=postgres password=<密码>');

-- ================================================
-- 2-1. wms_group
-- ================================================
INSERT INTO wms_group (id, store_name, address, contact, created_at)
SELECT
    group_id,
    store_name,
    address,
    CASE WHEN length(contact) > 32 THEN left(contact, 32) ELSE contact END,
    create_time AT TIME ZONE 'Asia/Shanghai'
FROM dblink('wms_src',
    'SELECT group_id, store_name, address, contact, create_time
     FROM groups WHERE group_id > 0'
) AS t(group_id INT, store_name VARCHAR(100), address VARCHAR(200),
       contact VARCHAR(50), create_time TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

SELECT setval('wms_group_id_seq', COALESCE((SELECT MAX(id) FROM wms_group), 1));

-- ================================================
-- 2-2. wms_user_profile（角色聚合在源库内完成，避免多次 dblink 往返）
-- ================================================
INSERT INTO wms_user_profile (user_id, group_id, role, nickname)
SELECT u_id::BIGINT, group_id, role, nickname
FROM dblink('wms_src',
    'SELECT
         u.id,
         u.group_id,
         CASE
             WHEN MAX(CASE WHEN a.authority = ''ROLE_ADMIN'' THEN 1 END) IS NOT NULL THEN ''ROLE_ADMIN''
             WHEN MAX(CASE WHEN a.authority = ''ROLE_OWNER'' THEN 1 END) IS NOT NULL THEN ''ROLE_OWNER''
             WHEN MAX(CASE WHEN a.authority = ''ROLE_STAFF'' THEN 1 END) IS NOT NULL THEN ''ROLE_STAFF''
             ELSE ''ROLE_DEFAULT''
         END AS role,
         ud.nickname
     FROM users u
     LEFT JOIN authorities a ON a.user_id = u.id
         AND a.authority IN (''ROLE_ADMIN'', ''ROLE_OWNER'', ''ROLE_STAFF'')
     LEFT JOIN users_detail ud ON ud.user_id = u.id
     GROUP BY u.id, u.group_id, ud.nickname'
) AS t(u_id INT, group_id INT, role VARCHAR(50), nickname VARCHAR(64))
ON CONFLICT (user_id) DO NOTHING;

-- ================================================
-- 2-3. wms_authority（细粒度 PERMISSION:* 权限）
-- ================================================
INSERT INTO wms_authority (user_id, authority)
SELECT u_id::BIGINT, authority
FROM dblink('wms_src',
    'SELECT user_id, authority FROM authorities WHERE authority LIKE ''PERMISSION:%'''
) AS t(u_id INT, authority VARCHAR(100))
ON CONFLICT DO NOTHING;

-- ================================================
-- 2-4. wms_category
-- ================================================
INSERT INTO wms_category (id, group_id, parent_id, name)
SELECT cate_id, group_id, parent_cate_id, name
FROM dblink('wms_src',
    'SELECT cate_id, group_id, parent_cate_id, name FROM category'
) AS t(cate_id INT, group_id INT, parent_cate_id INT, name VARCHAR(100))
ON CONFLICT (id) DO NOTHING;

SELECT setval('wms_category_id_seq', COALESCE((SELECT MAX(id) FROM wms_category), 1));

-- ================================================
-- 2-5. wms_merchandise
-- ================================================
INSERT INTO wms_merchandise (id, group_id, cate_id, cost, price, imei, sold, created_at)
SELECT me_id, group_id, cate_id, cost, price, imei, sold,
       create_time AT TIME ZONE 'Asia/Shanghai'
FROM dblink('wms_src',
    'SELECT me_id, group_id, cate_id, cost, price, imei, sold, create_time FROM merchandise'
) AS t(me_id INT, group_id INT, cate_id INT, cost NUMERIC, price NUMERIC,
       imei VARCHAR(50), sold BOOLEAN, create_time TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

SELECT setval('wms_merchandise_id_seq', COALESCE((SELECT MAX(id) FROM wms_merchandise), 1));

-- ================================================
-- 2-6. wms_order
-- ================================================
INSERT INTO wms_order (id, group_id, me_id, selling_price, selling_time, remark, is_returned)
SELECT order_id, group_id, me_id, selling_price,
       selling_time AT TIME ZONE 'Asia/Shanghai',
       remark, returned
FROM dblink('wms_src',
    'SELECT order_id, group_id, me_id, selling_price, selling_time, remark, returned FROM orders'
) AS t(order_id INT, group_id INT, me_id INT, selling_price NUMERIC,
       selling_time TIMESTAMP, remark TEXT, returned BOOLEAN)
ON CONFLICT (id) DO NOTHING;

SELECT setval('wms_order_id_seq', COALESCE((SELECT MAX(id) FROM wms_order), 1));

-- ================================================
-- 2-7. wms_join_request
-- ================================================
INSERT INTO wms_join_request (user_id, group_id, requested_at)
SELECT u_id::BIGINT, group_id, NOW()
FROM dblink('wms_src',
    'SELECT user_id, group_id FROM group_request'
) AS t(u_id INT, group_id INT)
ON CONFLICT (user_id) DO NOTHING;

-- ================================================
-- 2-8. wms_notice
-- ================================================
INSERT INTO wms_notice (id, group_id, type, content, created_at)
SELECT id, 0, type, content,
       publish_time AT TIME ZONE 'Asia/Shanghai'
FROM dblink('wms_src',
    'SELECT id, type, content, publish_time FROM notices'
) AS t(id INT, type VARCHAR(50), content TEXT, publish_time TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

SELECT setval('wms_notice_id_seq', COALESCE((SELECT MAX(id) FROM wms_notice), 1));

SELECT dblink_disconnect('wms_src');
```

---

### dblink 使用注意事项

| 问题 | 说明 |
|---|---|
| dblink 字符串内的单引号 | 必须双写 `''`，例如 `'ROLE_ADMIN'` 写成 `''ROLE_ADMIN''` |
| AS 列类型须匹配源库 | 若类型不兼容会报错；可先用 `TEXT` 接收再在外层 CAST |
| 命名连接作用域 | `dblink_connect` 的命名连接仅在当前**会话**（`\c` 切库后连接失效，需重新建立） |
| 网络延迟 | 大表建议分批迁移：在 dblink 查询中加 `LIMIT / OFFSET` 或 `WHERE id BETWEEN x AND y` |
| 源库只读建议 | 迁移期间对源库 `wms` 执行 `REVOKE INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public FROM app_user;`，防止业务写入污染迁移数据 |

---

## 八、相关文件

| 文件 | 说明 |
|---|---|
| [`V1__wms_cashier_schema.sql`](V1__wms_cashier_schema.sql) | wms_cashier 建表 DDL |
| [`wms-cashier/src/main/resources/schema.sql`](../../wms-cashier/src/main/resources/schema.sql) | 同上，服务启动自动执行 |
| [`auth-service/src/main/resources/schema.sql`](../../auth-service/src/main/resources/schema.sql) | auth 建表 DDL |

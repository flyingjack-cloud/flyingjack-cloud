-- 前提：user_id_mapping 表已在本库中可用
-- (old_int_id INT, new_bigint_id BIGINT)

-- 1. 迁移 wms_group（直接复制，id 不变）
INSERT INTO wms_group (id, store_name, address, contact, created_at)
SELECT id, name, address, contact, create_time
FROM old_group
ON CONFLICT DO NOTHING;

-- 2. 迁移 wms_user_profile（userId 转换为新 BIGINT）
INSERT INTO wms_user_profile (user_id, group_id, role, nickname)
SELECT m.new_bigint_id, u.group_id,
       CASE a.authority
           WHEN 'ROLE_ADMIN'  THEN 'ROLE_ADMIN'
           WHEN 'ROLE_OWNER'  THEN 'ROLE_OWNER'
           WHEN 'ROLE_STAFF'  THEN 'ROLE_STAFF'
           ELSE 'ROLE_DEFAULT'
       END AS role,
       p.nickname
FROM old_user u
JOIN user_id_mapping m ON u.id = m.old_int_id
LEFT JOIN old_authority a ON u.id = a.user_id
    AND a.authority IN ('ROLE_ADMIN','ROLE_OWNER','ROLE_STAFF','ROLE_DEFAULT')
LEFT JOIN old_user_profile p ON u.id = p.user_id
ON CONFLICT (user_id) DO NOTHING;

-- 3. 迁移 wms_authority（细粒度权限，userId 转换）
INSERT INTO wms_authority (user_id, authority)
SELECT m.new_bigint_id, a.authority
FROM old_authority a
JOIN user_id_mapping m ON a.user_id = m.old_int_id
WHERE a.authority LIKE 'PERMISSION:%'
ON CONFLICT DO NOTHING;

-- 4. 迁移 wms_category（无 user_id FK，直接复制）
INSERT INTO wms_category (id, group_id, parent_id, name)
SELECT id, group_id, parent_id, name FROM old_category
ON CONFLICT DO NOTHING;

-- 5. 迁移 wms_merchandise（无 user_id FK，直接复制）
INSERT INTO wms_merchandise (id, group_id, cate_id, cost, price, imei, sold, created_at)
SELECT id, group_id, cate_id, cost, price, imei, sold, create_time
FROM old_merchandise
ON CONFLICT DO NOTHING;

-- 6. 迁移 wms_order（无 user_id FK，直接复制）
INSERT INTO wms_order (id, group_id, me_id, selling_price, selling_time, remark, is_returned)
SELECT id, group_id, me_id, selling_price, selling_time, remark, is_returned
FROM old_order
ON CONFLICT DO NOTHING;

-- 7. 迁移 wms_join_request（userId 转换）
INSERT INTO wms_join_request (user_id, group_id, requested_at)
SELECT m.new_bigint_id, r.group_id, r.create_time
FROM old_join_request r
JOIN user_id_mapping m ON r.user_id = m.old_int_id
ON CONFLICT DO NOTHING;

-- 8. 迁移 wms_notice（无 user_id FK，直接复制）
INSERT INTO wms_notice (id, group_id, type, content, created_at)
SELECT id, group_id, type, content, create_time FROM old_notice
ON CONFLICT DO NOTHING;

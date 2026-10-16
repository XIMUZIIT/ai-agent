-- ============================================================
-- 给 AI 使用的视图：只暴露必要列，敏感信息在视图层脱敏
-- 表名/字段名请按真实库调整；视图名需与 application.yml 中的
-- agent.semantic 以及各 DomainModule 中声明的视图保持一致
-- ============================================================

CREATE OR REPLACE VIEW v_order_ai AS
SELECT
    o.order_no,
    CONCAT(LEFT(o.customer_name, 1), '**')  AS customer_name,   -- 姓名脱敏
    o.product_category,
    o.channel,
    o.pay_amount,
    o.status,                                                   -- PAID/COMPLETED/CANCELLED/REFUNDED
    o.create_time
FROM orders o;                                                  -- 不含手机号、地址、成本价

CREATE OR REPLACE VIEW v_inventory_ai AS
SELECT
    i.product_name,
    i.sku,
    i.stock_quantity,
    i.warehouse
FROM inventory i;

-- ------------------------------------------------------------
-- 只读账号：只授权这两个视图（对应 application.yml 中 agent.db 配置）
-- ------------------------------------------------------------
-- CREATE USER 'agent_ro'@'%' IDENTIFIED BY '******';
-- GRANT SELECT ON your_db.v_order_ai     TO 'agent_ro'@'%';
-- GRANT SELECT ON your_db.v_inventory_ai TO 'agent_ro'@'%';

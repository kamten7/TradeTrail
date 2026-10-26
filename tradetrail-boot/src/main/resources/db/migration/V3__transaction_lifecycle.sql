ALTER TABLE sales_order ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE sales_order ADD COLUMN fulfillment_hold BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sales_order ADD COLUMN address VARCHAR(500) NOT NULL DEFAULT '历史订单未记录地址';
ALTER TABLE sales_order ADD COLUMN pay_deadline TIMESTAMP NULL;
ALTER TABLE sales_order ADD COLUMN completed_at TIMESTAMP NULL;
UPDATE sales_order SET pay_deadline=TIMESTAMPADD(MINUTE, 30, created_at) WHERE pay_deadline IS NULL;
UPDATE sales_order SET status='AWAITING_SHIPMENT' WHERE status='PAID';
UPDATE sales_order SET status='CLOSED_UNPAID' WHERE status='CANCELLED';
CREATE TABLE order_item (
    order_id BIGINT NOT NULL, product_id BIGINT NOT NULL, product_name VARCHAR(120) NOT NULL,
    quantity INT NOT NULL, unit_price_cents BIGINT NOT NULL, PRIMARY KEY(order_id, product_id)
);
INSERT INTO order_item(order_id,product_id,product_name,quantity,unit_price_cents)
SELECT o.id,o.product_id,p.name,o.quantity,o.unit_price_cents FROM sales_order o JOIN product p ON p.id=o.product_id;
CREATE TABLE inventory_reservation (
    order_id BIGINT NOT NULL, product_id BIGINT NOT NULL, quantity INT NOT NULL,
    state VARCHAR(30) NOT NULL, PRIMARY KEY(order_id,product_id)
);
INSERT INTO inventory_reservation(order_id,product_id,quantity,state)
SELECT id,product_id,quantity,CASE WHEN status='PENDING_PAYMENT' THEN 'RESERVED'
WHEN status='CLOSED_UNPAID' THEN 'RELEASED' ELSE 'CONFIRMED' END FROM sales_order;
ALTER TABLE stock_ledger DROP INDEX uq_stock_action;
ALTER TABLE stock_ledger ADD CONSTRAINT uq_stock_action UNIQUE(order_id,product_id,action);
CREATE TABLE order_status_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, from_status VARCHAR(40),
    to_status VARCHAR(40) NOT NULL, event VARCHAR(60) NOT NULL, actor VARCHAR(80) NOT NULL,
    command_id VARCHAR(100) NOT NULL, trace_id VARCHAR(100) NOT NULL, occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE idempotency_record (
    command_key VARCHAR(240) PRIMARY KEY, fingerprint VARCHAR(64) NOT NULL,
    response_status INT, response_body TEXT, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE payment_attempt (
    id VARCHAR(64) PRIMARY KEY, order_id BIGINT NOT NULL, channel VARCHAR(30) NOT NULL,
    amount_cents BIGINT NOT NULL, status VARCHAR(30) NOT NULL, provider_status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(order_id)
);
INSERT INTO payment_attempt(id,order_id,channel,amount_cents,status,provider_status)
SELECT CONCAT('legacy-',id),id,'MOCK',total_cents,'SUCCEEDED','SUCCEEDED'
FROM sales_order WHERE status IN ('AWAITING_SHIPMENT','SHIPPED');
CREATE TABLE refund_attempt (
    id VARCHAR(64) PRIMARY KEY, payment_id VARCHAR(64) NOT NULL, order_id BIGINT NOT NULL,
    case_id BIGINT, amount_cents BIGINT NOT NULL, status VARCHAR(30) NOT NULL,
    reason VARCHAR(40) NOT NULL, attempts INT NOT NULL DEFAULT 0, last_error VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(payment_id)
);
ALTER TABLE after_sale_case ADD COLUMN type VARCHAR(30) NOT NULL DEFAULT 'REFUND_ONLY';
ALTER TABLE after_sale_case ADD COLUMN decision_reason VARCHAR(500);
ALTER TABLE after_sale_case ADD COLUMN return_tracking_no VARCHAR(100);
ALTER TABLE after_sale_case ADD COLUMN restocked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE after_sale_case ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
UPDATE after_sale_case SET type='RETURN_REFUND' WHERE order_id IN (SELECT id FROM sales_order WHERE status='SHIPPED');
UPDATE sales_order SET fulfillment_hold=TRUE WHERE id IN (SELECT order_id FROM after_sale_case WHERE status='APPLIED');
CREATE TABLE after_sale_action (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, case_id BIGINT NOT NULL, action VARCHAR(40) NOT NULL,
    actor VARCHAR(80) NOT NULL, reason VARCHAR(500), occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE shipment (
    order_id BIGINT PRIMARY KEY, carrier VARCHAR(30) NOT NULL, tracking_no VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL DEFAULT 'CREATED', progress INT NOT NULL DEFAULT 0,
    last_occurred_at TIMESTAMP NULL, delivered_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO shipment(order_id,carrier,tracking_no) SELECT id,'MOCK',tracking_no FROM sales_order WHERE tracking_no IS NOT NULL;
CREATE TABLE outbox_event (
    event_id VARCHAR(64) PRIMARY KEY, type VARCHAR(60) NOT NULL, aggregate_id VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL, state VARCHAR(20) NOT NULL DEFAULT 'PENDING', attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, last_error VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_outbox_due ON outbox_event(state,next_attempt_at);
CREATE TABLE inbox_event (
    consumer VARCHAR(60) NOT NULL, event_id VARCHAR(100) NOT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(consumer,event_id)
);
CREATE INDEX ix_order_deadline ON sales_order(status,pay_deadline);
CREATE INDEX ix_tracking_timeline ON tracking_event(tracking_no,occurred_at);

CREATE TABLE product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    price_cents BIGINT NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE stock (
    product_id BIGINT PRIMARY KEY,
    available INT NOT NULL,
    reserved INT NOT NULL DEFAULT 0,
    sold INT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE sales_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer VARCHAR(80) NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price_cents BIGINT NOT NULL,
    total_cents BIGINT NOT NULL,
    status VARCHAR(40) NOT NULL,
    request_id VARCHAR(100) NOT NULL,
    tracking_no VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_order_request UNIQUE (customer, request_id)
);

CREATE TABLE stock_ledger (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    action VARCHAR(40) NOT NULL,
    CONSTRAINT uq_stock_action UNIQUE (order_id, action)
);

CREATE TABLE payment_notice (
    event_id VARCHAR(100) PRIMARY KEY,
    order_id BIGINT NOT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE tracking_event (
    event_id VARCHAR(100) PRIMARY KEY,
    tracking_no VARCHAR(100) NOT NULL,
    status VARCHAR(40) NOT NULL,
    location VARCHAR(120) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE after_sale_case (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    customer VARCHAR(80) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(40) NOT NULL,
    CONSTRAINT uq_after_sale_order UNIQUE (order_id)
);

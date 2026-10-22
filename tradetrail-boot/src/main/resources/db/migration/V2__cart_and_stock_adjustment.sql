CREATE TABLE cart_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer VARCHAR(80) NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    selected BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_cart_product UNIQUE (customer, product_id)
);

CREATE TABLE stock_adjustment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    delta INT NOT NULL,
    reason VARCHAR(300) NOT NULL,
    operator_name VARCHAR(80) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

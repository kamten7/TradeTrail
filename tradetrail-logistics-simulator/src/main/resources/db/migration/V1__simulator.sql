CREATE TABLE sim_tracking_event (
    event_id VARCHAR(100) PRIMARY KEY,
    tracking_no VARCHAR(100) NOT NULL,
    status VARCHAR(40) NOT NULL,
    location VARCHAR(120) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    delivery_state VARCHAR(30) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(500)
);

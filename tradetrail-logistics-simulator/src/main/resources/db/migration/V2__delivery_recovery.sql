ALTER TABLE sim_tracking_event ADD COLUMN next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
CREATE INDEX ix_sim_due ON sim_tracking_event(delivery_state,next_attempt_at);

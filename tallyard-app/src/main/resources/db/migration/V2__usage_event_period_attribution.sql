-- Records which billing period each counted event was folded into. Replay of a period then reads
-- exactly the events that built its buckets (membership), independent of consumer lag or wall-clock
-- "as of" timestamps. NULL = not counted into any period (rejected, or applied as a late adjustment).
ALTER TABLE usage_event ADD COLUMN period_id uuid REFERENCES billing_period (id);
CREATE INDEX usage_event_period_idx ON usage_event (period_id, event_type) WHERE period_id IS NOT NULL;

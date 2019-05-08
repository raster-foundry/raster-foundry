ALTER TABLE metrics DROP COLUMN id;
ALTER TABLE metrics ADD CONSTRAINT metric_event_period_unique
  UNIQUE (period, metric_event, requester);

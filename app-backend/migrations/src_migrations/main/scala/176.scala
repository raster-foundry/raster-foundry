import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M176 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(176)(
    List(
      sqlu"""
CREATE TABLE metrics (
    id           UUID PRIMARY KEY NOT NULL,
    period       tsrange NOT NULL,
    metric_event jsonb NOT NULL,
    value        int NOT NULL,
    requester    varchar(255) REFERENCES users(id) NOT NULL
);

CREATE INDEX metrics_period_idx ON metrics(period);
CREATE INDEX metrics_metric_event_idx ON metrics USING GIN (metric_event);
"""
    ))
}

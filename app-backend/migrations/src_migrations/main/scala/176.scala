import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M176 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(176)(
    List(
      sqlu"""
-- requester doesn't reference users for auditability reasons,
-- so we can keep metrics after deleting users, if that ever happens
CREATE TABLE metrics (
    id           uuid PRIMARY KEY NOT NULL,
    period       tsrange NOT NULL,
    metric_event jsonb NOT NULL,
    metric_value int NOT NULL,
    requester    varchar(255) NOT NULL
);

CREATE INDEX metrics_period_idx ON metrics(period);
CREATE INDEX metrics_metric_event_idx ON metrics USING GIN (metric_event);
"""
    ))
}

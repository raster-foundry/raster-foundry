import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M178 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(178)(
    List(
      sqlu"""
ALTER TABLE metrics DROP COLUMN id;
ALTER TABLE metrics ADD CONSTRAINT metric_event_period_unique
  UNIQUE (period, metric_event, requester);
"""
    ))
}

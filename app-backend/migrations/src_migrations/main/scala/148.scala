import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M148 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(148)(
    List(
      sqlu"""
      ALTER TABLE projects ADD COLUMN extras JSONB default JSONB '{}';
      UPDATE projects SET extras = JSONB '{}';

      ALTER TABLE annotations
      ADD COLUMN labeled_by VARCHAR(255) REFERENCES users(id),
      ADD COLUMN verified_by VARCHAR(255) REFERENCES users(id);
    """
    ))
}

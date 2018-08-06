import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M148 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(148)(List(
    sqlu"""
      ALTER TABLE projects ADD COLUMN extras JSONB default JSONB '{}';
      UPDATE projects SET extras = JSONB '{}';

      
    """
  ))
}

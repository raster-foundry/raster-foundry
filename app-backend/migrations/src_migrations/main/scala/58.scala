import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M58 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(58)(
    List(
      sqlu"""
ALTER TABLE uploads ADD COLUMN project_id UUID DEFAULT NULL;
ALTER TABLE uploads ADD CONSTRAINT upload_project_fkey FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE SET NULL;
"""
    ))
}

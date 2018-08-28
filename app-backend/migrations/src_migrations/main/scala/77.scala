import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M77 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(77)(
    List(
      sqlu"""
ALTER TABLE map_tokens
  DROP CONSTRAINT map_tokens_project_id_fkey,
  ADD CONSTRAINT map_tokens_project_id_fkey
    FOREIGN KEY (project_id)
    REFERENCES projects(id)
    ON DELETE CASCADE;
"""
    ))
}

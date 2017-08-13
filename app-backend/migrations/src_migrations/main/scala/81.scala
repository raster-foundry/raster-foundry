import slick.driver.PostgresDriver.api._
import com.liyaos.forklift.slick.SqlMigration

object M81 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(81)(List(
    sqlu"""
ALTER TABLE map_tokens ALTER COLUMN project_id DROP NOT NULL;
ALTER TABLE map_tokens ADD COLUMN toolrun_id UUID NULL;
ALTER TABLE map_tokens ADD CONSTRAINT map_tokens_toolrun_id_fkey FOREIGN KEY (toolrun_id) REFERENCES tool_runs(id) ON DELETE CASCADE;
CREATE UNIQUE INDEX map_token_project_idx ON map_tokens (project_id);
CREATE UNIQUE INDEX map_token_toolrun_idx ON map_tokens (toolrun_id);
"""
  ))
}

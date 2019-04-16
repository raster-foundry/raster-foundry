import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M171 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(171)(
    List(
      sqlu"""
ALTER TABLE tool_runs
DROP CONSTRAINT tool_runs_project_id_fkey,
DROP CONSTRAINT tool_runs_project_layer_id_fkey,
DROP CONSTRAINT tool_runs_template_id_fkey;

ALTER TABLE tool_runs

-- deletion of project layers and projects probably means data has gone missing
-- and that tools runs can't be run anymore, so delete the tool run
ADD CONSTRAINT tool_runs_project_id_fkey FOREIGN KEY (project_id) REFERENCES projects (id)
  ON DELETE CASCADE,
ADD CONSTRAINT tool_runs_project_layer_id_fkey FOREIGN KEY (project_layer_id) REFERENCES project_layers (id)
  ON DELETE CASCADE,

-- no reason to delete a tool run just because the template that created it has gone missing,
-- since the tool run doesn't use data from the template anywhere, so /shrug
ADD CONSTRAINT tool_runs_template_id_fkey FOREIGN KEY (template_id) REFERENCES tools (id)
  ON DELETE SET NULL;
"""
    ))
}

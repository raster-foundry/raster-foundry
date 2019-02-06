import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M161 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(161)(
    List(
      sqlu"""
ALTER TABLE tool_runs
ADD COLUMN project_id uuid REFERENCES projects(id),
ADD COLUMN project_layer_id uuid REFERENCES project_layers(id),
ADD COLUMN template_id uuid REFERENCES tools(id)
"""
    ))
}

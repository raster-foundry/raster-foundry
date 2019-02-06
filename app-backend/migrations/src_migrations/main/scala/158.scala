import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M158 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(158)(
    List(
      sqlu"""
    -- Populate scenes_to_layers table based on the joined result of scenes_to_projects and projects tables
    -- This command adds relationship between scenes and current projects' default layers
    INSERT INTO scenes_to_layers (
      SELECT
        stp.scene_id, p.default_layer_id AS project_layer_id, stp.scene_order, stp.mosaic_definition, stp.accepted
      FROM scenes_to_projects AS stp
      JOIN projects p ON stp.project_id = p.id
    )
    ON CONFLICT ON CONSTRAINT scenes_to_layers_pkey DO NOTHING;
    """
    ))
}

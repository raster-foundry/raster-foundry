import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M162 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(162)(
    List(
      sqlu"""
    ALTER TABLE annotations
      ADD COLUMN project_layer_id UUID,
      ADD CONSTRAINT annotations_project_layer_id_fkey
      FOREIGN KEY (project_layer_id)
      REFERENCES project_layers(id)
      ON DELETE CASCADE;

    UPDATE annotations a
    SET project_layer_id = p.default_layer_id
    FROM projects p
    WHERE a.project_id = p.id;

    ALTER TABLE annotations
      ALTER COLUMN project_layer_id
      SET NOT NULL;

    ALTER TABLE annotation_groups
      ADD COLUMN project_layer_id UUID,
      ADD CONSTRAINT annotation_groups_project_layer_id_fkey
      FOREIGN KEY (project_layer_id)
      REFERENCES project_layers(id)
      ON DELETE CASCADE;

    UPDATE annotation_groups ag
    SET project_layer_id = p.default_layer_id
    FROM projects p
    WHERE ag.project_id = p.id;

    ALTER TABLE annotation_groups
      ALTER COLUMN project_layer_id
      SET NOT NULL;
    """
    ))
}

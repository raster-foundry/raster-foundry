import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M175 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(175)(
    List(
      sqlu"""
    ALTER TABLE exports DROP CONSTRAINT exports_project_layer_id_fkey;
    ALTER TABLE exports ADD CONSTRAINT exports_project_layer_id_fkey FOREIGN KEY (project_layer_id) REFERENCES project_layers(id) ON DELETE CASCADE;
    """
    ))
}

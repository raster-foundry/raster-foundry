import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M173 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(173)(
    List(
      sqlu"""
    ALTER TABLE uploads DROP CONSTRAINT uploads_layer_id_fkey;
    ALTER TABLE uploads ADD CONSTRAINT uploads_layer_id_fkey FOREIGN KEY (layer_id) REFERENCES project_layers(id) ON DELETE SET NULL;
    """
    ))
}

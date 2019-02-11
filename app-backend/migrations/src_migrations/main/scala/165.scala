import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M165 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(165)(List(
    sqlu"""
    ALTER TABLE exports ADD COLUMN project_layer_id UUID REFERENCES project_layers(id);
    """
  ))
}

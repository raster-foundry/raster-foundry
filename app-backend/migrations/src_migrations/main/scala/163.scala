import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M163 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(163)(
    List(
      sqlu"""
ALTER TABLE uploads ADD COLUMN layer_id UUID REFERENCES project_layers(id);
"""
    ))
}

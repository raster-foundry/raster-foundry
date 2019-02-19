import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M167 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(167)(
    List(
      sqlu"""
    UPDATE exports
    SET project_layer_id = (
      SELECT default_layer_id
      FROM projects
      WHERE projects.id = exports.project_id
    )
    WHERE project_id IS NOT NULL;
    """
    ))

}

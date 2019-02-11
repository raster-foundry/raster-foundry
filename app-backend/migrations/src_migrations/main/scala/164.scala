import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M164 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(164)(
    List(
      sqlu"""
    UPDATE project_layers SET color_group_hex = '#738FFC' WHERE color_group_hex = '#FFFFFF';
    """
    ))
}

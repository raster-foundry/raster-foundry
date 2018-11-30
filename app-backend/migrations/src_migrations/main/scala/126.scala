import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M126 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(126)(
    List(
      sqlu"""
    DELETE FROM access_control_rules WHERE subject_type = 'ALL' AND object_type = 'SCENE' AND
      (action_type = 'VIEW' OR action_type = 'DOWNLOAD');
    """
    ))
}

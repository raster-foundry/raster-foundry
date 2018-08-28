import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M136 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(136)(
    List(
      sqlu"""
      UPDATE annotations
      SET label = 'Unlabeled'
      WHERE label = '';
    """
    ))
}

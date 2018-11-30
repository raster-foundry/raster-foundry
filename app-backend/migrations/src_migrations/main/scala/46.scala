import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M46 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(46)(
    List(
      sqlu"""
           ALTER TABLE projects ADD COLUMN tile_visibility visibility DEFAULT 'PRIVATE' NOT NULL;
  """
    ))
}

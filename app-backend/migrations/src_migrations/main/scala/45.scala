import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M45 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(45)(
    List(
      sqlu"""
ALTER TABLE projects ADD COLUMN extent geometry(Polygon, 3857);
"""
    ))
}

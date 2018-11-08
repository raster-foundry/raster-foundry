import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M153 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(153)(
    List(
      sqlu"""
UPDATE datasources SET bands = '[]' WHERE bands = '{}';
"""
    )
  )
}

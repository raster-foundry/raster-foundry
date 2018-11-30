import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M67 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(67)(
    List(
      sqlu"""
ALTER TABLE scenes DROP CONSTRAINT scene_name_org_datasource;
"""
    ))
}

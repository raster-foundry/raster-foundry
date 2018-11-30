import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M44 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(44)(
    List(
      sqlu"""
ALTER TABLE scenes
    ADD CONSTRAINT scene_name_org_datasource UNIQUE (name, organization_id, datasource);
"""
    ))
}

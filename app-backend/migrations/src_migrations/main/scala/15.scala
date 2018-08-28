import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M15 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(15)(
    List(
      sqlu"""
ALTER TABLE scenes
    ADD COLUMN name VARCHAR(255);

UPDATE scenes SET name = id;

ALTER TABLE scenes
    ADD CONSTRAINT scene_name_org_datasource UNIQUE (name, organization_id, datasource);

ALTER TABLE scenes
    ALTER COLUMN name SET NOT NULL;
"""
    ))
}

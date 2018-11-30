import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M145 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(145)(
    List(
      sqlu"""
ALTER TABLE aois ADD COLUMN shape UUID REFERENCES shapes(id);

ALTER TABLE shapes ALTER geometry SET NOT NULL;
ALTER TABLE shapes ADD COLUMN temp_aoi UUID;

INSERT INTO shapes (
id, created_at, modified_at, created_by, modified_by, owner, name, geometry, temp_aoi
) (
  SELECT
  uuid_generate_v4(), a.created_at, a.modified_at, a.created_by, a.modified_by,
  a.owner, 'AOI: ' || p.name, a.area, a.id
  FROM aois a join projects p on a.project_id = p.id
);

UPDATE aois SET shape = (select id from shapes where temp_aoi = aois.id);

ALTER TABLE shapes DROP COLUMN temp_aoi;
ALTER TABLE aois ALTER shape SET NOT NULL;
ALTER TABLE aois DROP COLUMN area;

"""
    ))
}

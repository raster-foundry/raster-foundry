import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M127 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(127)(
    List(
      sqlu"""
-- Landsat 5 MSS -> Landsat 4 MSS
UPDATE scenes
SET datasource = 'b5d02569-11f7-473d-be82-d1d4e1ce45f3'
WHERE datasource = '7b205ec9-6cce-444d-998a-f34d379258b2';

UPDATE uploads
SET datasource = 'b5d02569-11f7-473d-be82-d1d4e1ce45f3'
WHERE datasource = '7b205ec9-6cce-444d-998a-f34d379258b2';

-- Landsat 5 TM -> Landsat 4 TM
UPDATE scenes
SET datasource = 'e8c4d923-5a73-430d-8fe4-53bd6a12ce6a'
WHERE datasource = 'a7b4a88e-4f69-477f-9784-343fb775ab12';

UPDATE uploads
SET datasource = 'e8c4d923-5a73-430d-8fe4-53bd6a12ce6a'
WHERE datasource = 'a7b4a88e-4f69-477f-9784-343fb775ab12';

-- Delete Landsat 5 TM and MSS
DELETE FROM datasources
WHERE id in ('a7b4a88e-4f69-477f-9784-343fb775ab12', '7b205ec9-6cce-444d-998a-f34d379258b2');

DELETE FROM access_control_rules
WHERE object_id in ('a7b4a88e-4f69-477f-9784-343fb775ab12', '7b205ec9-6cce-444d-998a-f34d379258b2')
AND object_type = 'DATASOURCE';

UPDATE datasources
SET name = 'Landsat 4 + 5 Thematic Mapper (TM)'
WHERE id = 'e8c4d923-5a73-430d-8fe4-53bd6a12ce6a';

UPDATE datasources
SET name = 'Landsat Tri-Decadal Multispectral Scanner (MSS)'
WHERE id = 'b5d02569-11f7-473d-be82-d1d4e1ce45f3';
"""
    ))
}

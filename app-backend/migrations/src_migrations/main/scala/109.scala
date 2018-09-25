import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M109 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(109)(
    List(
      sqlu"""
UPDATE datasources SET composites = '{"natural": {"label": "Natural Color", "value": {"redBand": 0, "blueBand": 2, "greenBand": 3}}, "burn-veg": {"label": "Band 7-2-1 Combination", "value": {"redBand": 6, "blueBand": 0, "greenBand": 1}}, "water-veg": {"label": "Band 3-6-7 Combination", "value": {"redBand": 2, "blueBand": 6, "greenBand": 5}}}' WHERE id = 'a11b768b-d869-476e-a1ed-0ac3205ed761' OR id = '55735945-9da5-47c3-8ae4-572b5e11205b';

UPDATE feature_flags SET active = 'true' where id = 'd73087dd-5047-4d79-b82c-01a7feef9068' OR id = 'c3d1e27e-5e25-4cff-9d03-af310e3c2dc4';

UPDATE datasources SET name = 'M0D09A1' WHERE id = 'a11b768b-d869-476e-a1ed-0ac3205ed761';
UPDATE datasources SET name = 'MYD09A1' WHERE id = '55735945-9da5-47c3-8ae4-572b5e11205b';
"""
    ))
}

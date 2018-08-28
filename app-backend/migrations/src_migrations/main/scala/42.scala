import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M42 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(42)(
    List(
      sqlu"""
ALTER TABLE datasources
ADD COLUMN composites JSONB default '{}';

UPDATE datasources set (composites) = (
'{"natural":{"label":"Natural Color","value":{"redBand":3,"greenBand":2,"blueBand":1}},"cir":{"label":"Color infrared","value":{"redBand":4,"greenBand":3,"blueBand":2}},"urban":{"label":"Urban","value":{"redBand":6,"greenBand":5,"blueBand":4}},"water":{"label":"Water","value":{"redBand":4,"greenBand":5,"blueBand":3}},"atmosphere":{"label":"Atmosphere penetration","value":{"redBand":6,"greenBand":4,"blueBand":2}},"agriculture":{"label":"Agriculture","value":{"redBand":5,"greenBand":4,"blueBand":1}},"forestfire":{"label":"Forest Fire","value":{"redBand":6,"greenBand":4,"blueBand":1}},"bareearth":{"label":"Bare Earth change detection","value":{"redBand":5,"greenBand":2,"blueBand":1}},"vegwater":{"label":"Vegetation & water","value":{"redBand":4,"greenBand":6,"blueBand":0}}}'
) where datasources.id = '697a0b91-b7a8-446e-842c-97cda155554d';

UPDATE datasources set (composites) = (
'{"invalid":{"label":"Invalid Bands","value":{"redBand":0,"greenBand":0,"blueBand":0}}}'
) where datasources.id = '4a50cb75-815d-4fe5-8bc1-144729ce5b42';
"""
    ))
}

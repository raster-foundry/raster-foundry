import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M85 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(85)(
    List(
      sqlu"""
UPDATE datasources SET (composites) = (
  '{
    "natural":{"label":"Natural Color","value":{"redBand":4,"greenBand":3,"blueBand":2}}
   }'
) where datasources.id = '4a50cb75-815d-4fe5-8bc1-144729ce5b42';
"""
    ))
}

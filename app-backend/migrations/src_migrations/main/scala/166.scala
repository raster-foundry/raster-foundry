import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M166 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(166)(
    List(
      sqlu"""
UPDATE datasources
SET
  composites = composites || '{
    "default": {"label": "default", "value": {"redBand": 6, "greenBand": 4, "blueBand": 2}}
  }' :: jsonb
WHERE
  id = 'e8c4d923-5a73-430d-8fe4-53bd6a12ce6a';
"""
    ))
}

import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M88 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(88)(
    List(
      sqlu"""

INSERT INTO datasources (
    id,
    created_at,
    created_by,
    modified_at,
    modified_by,
    organization_id,
    name,
    visibility,
    extras,
    composites,
    owner,
    bands
)
    SELECT
        'c33db82d-afdb-43cb-a6ac-ba899e48638d',
        created_at,
        created_by,
        modified_at,
        modified_by,
        organization_id,
        'Sentinel-2B',
        visibility,
        extras,
        composites,
        owner,
        bands
    FROM datasources
    WHERE id = '4a50cb75-815d-4fe5-8bc1-144729ce5b42';

UPDATE datasources
SET name = 'Sentinel-2A'
WHERE id = '4a50cb75-815d-4fe5-8bc1-144729ce5b42';

"""
    ))
}

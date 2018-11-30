import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M39 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(39)(
    List(
      sqlu"""
INSERT INTO tools (
  id, created_at, modified_at, created_by, modified_by, organization_id,
  title, description, requirements, license, visibility, compatible_data_sources,
  stars
) VALUES (
  'b237a825-203e-44bc-9cfa-81cff9f28641',
  now(),
  now(),
  'default',
  'default',
  '9e2bef18-3f46-426b-a5bd-9913ee1ff840',
  'NDVI Change Detection',
  'Compares the change in vegetation over time from two compatible projects.',
  '2 scenes, each with NIR-1 and red band information.',
  '',
  'PUBLIC',
  '{"Sentinel-2", "Landsat 8"}',
  '5.0'
)
    """
    ))
}

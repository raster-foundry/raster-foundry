import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M151 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(151)(
    List(
      sqlu"""
UPDATE projects
SET single_band_options = single_band_options || '{"extraNoData": []}'
WHERE single_band_options is not null;
"""
    ))
}

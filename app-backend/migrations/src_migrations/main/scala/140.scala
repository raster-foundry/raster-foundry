import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M140 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(140)(
    List(
      sqlu"CREATE INDEX IF NOT EXISTS tile_footprint_idx ON scenes USING GIST (tile_footprint);"
    ))
}

import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M174 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(174)(
    List(
      sqlu"""
      ALTER TABLE project_layers
        ADD COLUMN overviews_location TEXT,
        ADD COLUMN min_zoom_level INTEGER;
      """
    ))
}

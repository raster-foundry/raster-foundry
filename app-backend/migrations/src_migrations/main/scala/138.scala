import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M138 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(138)(
    List(
      sqlu"""
      UPDATE scenes
      SET cloud_cover = cast(scene_metadata ->> 'cloudyPixelPercentage' as  REAL)
      WHERE datasource = uuid('4a50cb75-815d-4fe5-8bc1-144729ce5b42');
    """
    ))
}

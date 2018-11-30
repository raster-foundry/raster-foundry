import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M17 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(17)(
    List(
      sqlu"""
    DROP TYPE thumbnailsize CASCADE;
    CREATE TYPE thumbnailsize AS ENUM ('SMALL', 'LARGE', 'SQUARE');

    ALTER TABLE thumbnails ADD COLUMN thumbnail_size thumbnailsize NOT NULL;
    """
    ))
}

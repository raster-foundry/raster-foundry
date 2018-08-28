import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M117 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(117)(
    List(
      sqlu"""
      CREATE TYPE user_visibility AS ENUM ('PUBLIC', 'PRIVATE');

      ALTER TABLE users Add COLUMN visibility user_visibility default 'PRIVATE'::user_visibility;
    """
    ))
}

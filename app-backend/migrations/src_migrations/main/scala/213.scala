import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M213 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(213)(List(
    sqlu"""
      CREATE TYPE user_visibility AS ENUM ('PUBLIC', 'PRIVATE');

      ALTER TABLE users Add COLUMN visibility user_visibility default 'PRIVATE'::user_visibility;
    """
  ))
}

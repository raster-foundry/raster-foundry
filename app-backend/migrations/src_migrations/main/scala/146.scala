import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M146 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(146)(
    List(
      sqlu"""
      CREATE INDEX IF NOT EXISTS datasource_idx ON scenes(datasource);
    """
    ))
}

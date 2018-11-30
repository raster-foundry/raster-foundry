import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M19 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(19)(
    List(
      sqlu"DROP TABLE footprints;"
    ))
}

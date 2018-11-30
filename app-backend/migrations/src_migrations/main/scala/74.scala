import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M74 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(74)(
    List(
      sqlu"ALTER TABLE users ADD COLUMN planet_credential text;"
    ))
}

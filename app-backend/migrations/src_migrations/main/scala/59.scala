import slick.driver.PostgresDriver.api._
import com.liyaos.forklift.slick.SqlMigration

object M59 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(59)(List(
    sqlu"ALTER TABLE users ADD COLUMN dropbox_credential text;"
  ))
}

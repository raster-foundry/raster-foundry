import slick.driver.PostgresDriver.api._
import com.liyaos.forklift.slick.SqlMigration

object M75 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(75)(List(
    sqlu"ALTER TABLE uploads ADD COLUMN source text;"
  ))
}

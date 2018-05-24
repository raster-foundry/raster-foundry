import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M118 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(118)(List(
    sqlu"" // your sql code goes here
  ))
}

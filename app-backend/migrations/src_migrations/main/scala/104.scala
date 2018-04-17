import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M104 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(104)(List(
    sqlu"" // your sql code goes here
  ))
}

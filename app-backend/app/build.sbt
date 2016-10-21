name := "raster-foundry-app"

initialCommands in console := """
  |import com.azavea.rf.utils.Config
  |import com.azavea.rf._
  |import com.azavea.rf.datamodel._
  |import com.azavea.rf.database.Database
  |import com.azavea.rf.database.ExtendedPostgresDriver.api._
  |import com.azavea.rf.database.tables._
  |import java.util.UUID
  |import java.sql.Timestamp
  |import java.time.Instant
  |import scala.concurrent.{Future,Await}
  |import scala.concurrent.duration._
  |val publicOrgId = UUID.fromString("dfac6307-b5ef-43f7-beda-b9f208bb7726")
  |object Main extends Config { implicit val database = new Database(jdbcUrl, dbUser, dbPassword)}
  |import Main._
""".trim.stripMargin

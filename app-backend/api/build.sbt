name := "api"

assemblyJarName in assembly := "api-assembly.jar"

initialCommands in console := """
  |import com.rasterfoundry.api.utils.Config
  |import com.rasterfoundry.api._
  |import com.rasterfoundry.datamodel._
  |import io.circe._
  |import io.circe.syntax._
  |import java.util.UUID
  |import java.sql.Timestamp
  |import java.time.Instant
  |import scala.concurrent.{Future,Await}
  |import scala.concurrent.duration._
  |import akka.actor.ActorSystem
  |import akka.stream.ActorMaterializer
  |val publicOrgId = UUID.fromString("dfac6307-b5ef-43f7-beda-b9f208bb7726")
  |import geotrellis.vector.{MultiPolygon, Polygon, Point, Geometry, Projected}
  |object Rollbar extends com.rasterfoundry.common.RollbarNotifier {
  |  implicit val system = ActorSystem("rf-system")
  |  implicit val materializer = ActorMaterializer()
  |}
  |object Main extends Config
  |import Main._
""".trim.stripMargin

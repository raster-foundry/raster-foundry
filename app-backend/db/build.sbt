initialCommands in console :=
  """
import com.azavea.rf.database._
import com.azavea.rf.database.Implicits._

import doobie._, doobie.implicits._
import doobie.hikari._, doobie.hikari.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._
import com.azavea.rf.datamodel._
import doobie.util.log.LogHandler
import java.util.UUID

implicit val han = LogHandler({ e => println("*** " + e) })

// implicit transactor for console testing
implicit val xa = Transactor.fromDriverManager[IO](
  "org.postgresql.Driver", "jdbc:postgresql://database.service.rasterfoundry.internal/", "rasterfoundry", "rasterfoundry"
)
val y = xa.yolo; import y._
"""

testOptions in Test += Tests.Argument("-oD")

import com.azavea.rf.datamodel.v3.schema.tables.{Users, UsersRow}
import com.liyaos.forklift.slick.DBIOMigration
import java.sql.Timestamp
import java.util.{Calendar, Date, UUID}
import slick.driver.PostgresDriver.api._

object M4 {
  val now = new Timestamp((new Date()).getTime())

  RFMigrations.migrations = RFMigrations.migrations :+ DBIOMigration(4)(
    DBIO.seq(Users ++= Seq(
      UsersRow(
        UUID.fromString("827d885a-b8f6-447f-8795-21c688af56e0"),
        now,
        now,
        Some(true),
        Some(true),
        "info+raster.foundry@azavea.com",
        "Root",
        "User",
        UUID.fromString("9e2bef18-3f46-426b-a5bd-9913ee1ff840")
      )
    )))
}

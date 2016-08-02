import com.azavea.rf.datamodel.v1.schema.tables.{Organizations, OrganizationsRow}
import com.liyaos.forklift.slick.DBIOMigration
import java.sql.Timestamp
import java.util.{Calendar, Date, UUID}
import slick.driver.PostgresDriver.api._

/**
  * Data migration that adds a root organization
  */
object M2 {
  val now = new Timestamp((new Date()).getTime())

  RFMigrations.migrations = RFMigrations.migrations :+ DBIOMigration(2)(
    DBIO.seq(Organizations ++= Seq(
      OrganizationsRow(
        UUID.fromString("9e2bef18-3f46-426b-a5bd-9913ee1ff840"), // id
        now,                                                     // created_at
        now,                                                     // modified_at
        "Root organization"                                      // name
      )
    )))
}

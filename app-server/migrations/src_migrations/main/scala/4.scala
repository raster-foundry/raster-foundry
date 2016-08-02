import com.azavea.rf.datamodel.v3.schema.tables.{Users, UsersRow}
import com.liyaos.forklift.slick.DBIOMigration
import java.sql.Timestamp
import java.util.{Calendar, Date, UUID}
import slick.driver.PostgresDriver.api._

/**
  * Data migration that adds a root user, who is a member of
  * the root organization.
  */
object M4 {
  val now = new Timestamp((new Date()).getTime())

  RFMigrations.migrations = RFMigrations.migrations :+ DBIOMigration(4)(
    DBIO.seq(Users ++= Seq(
      UsersRow(
        UUID.fromString("827d885a-b8f6-447f-8795-21c688af56e0"), // id
        now,                                                     // created_at
        now,                                                     // modified_at
        Some(true),                                              // is_active
        Some(true),                                              // is_staff
        "info+raster.foundry@azavea.com",                        // email
        "Root",                                                  // first_name
        "User",                                                  // last_name
        UUID.fromString("9e2bef18-3f46-426b-a5bd-9913ee1ff840")  // organization_id
      )
    )))
}

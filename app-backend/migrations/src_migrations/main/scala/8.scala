import slick.driver.PostgresDriver.api._
import com.liyaos.forklift.slick.DBIOMigration

import java.util.{Calendar, Date, UUID}
import java.sql.Timestamp

import com.azavea.rf.datamodel.v7.schema.tables.{
  UsersToOrganizations, UsersToOrganizationsRow
}


object M8 {
  val now = new Timestamp((new Date()).getTime())

  RFMigrations.migrations = RFMigrations.migrations :+ DBIOMigration(8)(
    DBIO.seq(
      UsersToOrganizations ++= Seq(
        UsersToOrganizationsRow(
          "default", // userId
          UUID.fromString("dfac6307-b5ef-43f7-beda-b9f208bb7726"), // organizationId
          "viewer",
          now,
          now
        ))))
}

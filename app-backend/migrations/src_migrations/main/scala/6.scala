import slick.driver.PostgresDriver.api._
import com.liyaos.forklift.slick.DBIOMigration

import java.util.{Calendar, Date, UUID}
import java.sql.Timestamp

import com.azavea.rf.datamodel.v5.schema.tables.{Users, UsersRow}
import com.azavea.rf.datamodel.v5.schema.tables.{Organizations, OrganizationsRow}


object M6 {
  val now = new Timestamp((new Date()).getTime())

  RFMigrations.migrations = RFMigrations.migrations :+ DBIOMigration(6)(
    DBIO.seq(
      Organizations ++= Seq(
        OrganizationsRow(
          UUID.fromString("dfac6307-b5ef-43f7-beda-b9f208bb7726"), // id
          now,                                                     // created_at
          now,                                                     // modified_at
          "Public"                                                 // name
        )),
      Users ++= Seq(
        UsersRow(
          UUID.fromString("c871dbc2-4d5e-445e-a7b0-896bf0920859"),   // id
          UUID.fromString("dfac6307-b5ef-43f7-beda-b9f208bb7726"),   // organization_id
          Option("default")                                          // auth_id
        ))))
}

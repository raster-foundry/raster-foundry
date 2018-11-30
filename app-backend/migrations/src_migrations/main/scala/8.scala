import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.{SqlMigration}

import java.util.{Calendar, Date, UUID}

object M8 {

  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(8)(
    List(sqlu"""
      INSERT INTO users_to_organizations (user_id, organization_id, role, created_at, modified_at)
      VALUES (
          'default',
          'dfac6307-b5ef-43f7-beda-b9f208bb7726',
          'viewer',
          now(),
          now()
      );"""))
}

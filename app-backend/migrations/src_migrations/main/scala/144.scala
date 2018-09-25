import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M144 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(144)(
    List(
      sqlu"""
    CREATE TYPE organization_type AS ENUM ('COMMERCIAL', 'GOVERNMENT', 'NON-PROFIT', 'ACADEMIC', 'MILITARY', 'OTHER');

    ALTER TABLE users
    ADD COLUMN personal_info JSONB NOT NULL default
      JSONB '{
        "firstName": "",
        "lastName": "",
        "email": "",
        "emailNotifications": false,
        "phoneNumber": "",
        "organizationName": "",
        "organizationType": "OTHER",
        "organizationWebsite": "",
        "profileWebsite": "",
        "profileBio": "",
        "profileUrl": ""
      }';
    """
    ))
}

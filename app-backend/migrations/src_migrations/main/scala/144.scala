import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M144 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(144)(List(
    sqlu"""
    ALTER TABLE users
    ADD COLUMN personal_info JSONB NOT NULL default
      JSONB '{
        "firstName": "",
        "lastName": "",
        "email": "",
        "emailNotifications": false,
        "phoneNumber": "",
        "organizationName": "",
        "organizationType": "",
        "organizationWebsite": "",
        "profileWebsite": "",
        "profileBio": "",
        "profileUrl": ""
      }';
    """
  ))
}

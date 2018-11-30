import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M123 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(123)(
    List(
      sqlu"""
    ALTER TABLE platforms
      RENAME COLUMN settings
      TO public_settings;

    ALTER TABLE platforms
    ALTER COLUMN public_settings
    SET DEFAULT
      JSONB '{
        "emailUser": "",
        "emailSmtpHost": "",
        "emailIngestNotification": false,
        "emailAoiNotification": false
      }';

    UPDATE platforms
    SET public_settings = JSONB '{
      "emailUser": "",
      "emailSmtpHost": "",
      "emailIngestNotification": false,
      "emailAoiNotification": false
    }';

    ALTER TABLE platforms
      ADD COLUMN private_settings JSONB NOT NULL default
        JSONB '{
          "emailPassword": ""
        }';
    """
    ))
}

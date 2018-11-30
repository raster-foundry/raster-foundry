import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M131 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(131)(
    List(
      sqlu"""
      ALTER TABLE platforms
      ALTER COLUMN public_settings
      SET DEFAULT
        JSONB '{
          "emailUser": "",
          "emailSmtpHost": "",
          "emailSmtpPort": 465,
          "emailSmtpEncryption": "ssl",
          "emailIngestNotification": false,
          "emailAoiNotification": false,
          "emailExportNotification": false
        }';

      UPDATE platforms
      SET public_settings = json_build_object(
        'emailUser', public_settings->'emailUser',
        'emailSmtpHost', public_settings->'emailSmtpHost',
        'emailSmtpPort', 465,
        'emailSmtpEncryption', 'ssl',
        'emailIngestNotification', public_settings->'emailIngestNotification',
        'emailAoiNotification', public_settings->'emailAoiNotification',
        'emailExportNotification', false
      )
    """
    ))
}

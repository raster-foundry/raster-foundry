import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M154 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(154)(
    List(
      sqlu"""
      ALTER TABLE platforms
      ALTER COLUMN public_settings
      SET DEFAULT
        '{
          "emailSmtpUserName": "",
          "emailSmtpHost": "",
          "emailSmtpPort": 465,
          "emailSmtpEncryption": "ssl",
          "emailIngestNotification": false,
          "emailAoiNotification": false,
          "emailExportNotification": false,
          "platformHost": null,
          "emailFrom": "noreply@example.com",
          "emailFromDisplayName": "",
          "emailSupport": "support@example.com"
        }'::jsonb;

      UPDATE platforms
      SET public_settings =
        public_settings - 'emailUser' || jsonb_build_object(
          'emailSmtpUserName', public_settings->'emailUser',
          'emailFrom', 'noreply@example.com',
          'emailFromDisplayName', '',
          'emailSupport', 'support@example.com'
        );
    """
    ))
}

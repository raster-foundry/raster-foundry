import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M90 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(90)(
    List(
      sqlu"""
    INSERT INTO feature_flags (id, key, active, name, description) VALUES
      (
        'd73087dd-5047-4d79-b82c-01a7feef9068',
        'external-source-browse',
        FALSE,
        'External source browse',
        'Allow users to browse imagery from external providers'
      ),
      (
        'e8eeb7b0-dc6b-466f-b708-41861a997f66',
        'external-source-browse-planet',
        FALSE,
        'External source browse - Planet',
        'Allow users to browse planet imagery'
      );
    """
    ))
}

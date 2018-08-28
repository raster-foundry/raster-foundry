import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M97 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(97)(
    List(
      sqlu"""
    INSERT INTO feature_flags (id, key, active, name, description) VALUES
      (
        'c3d1e27e-5e25-4cff-9d03-af310e3c2dc4',
        'external-source-browse-cmr',
        FALSE,
        'External source browse - CMR',
        'Allow users to browse CMR collections'
      );
    """
    ))
}

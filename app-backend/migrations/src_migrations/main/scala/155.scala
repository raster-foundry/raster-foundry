import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M155 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(155)(
    List(
      sqlu"""
      INSERT INTO feature_flags (id, name, key, active, description) values (
        uuid_generate_v4(),
        'Anlyses Sharing',
        'analyses-sharing',
        false,
        'Allow sharing analyses and let users to switch between their own analyses and anlyses that have been shared with them'
      );
    """
    ))
}

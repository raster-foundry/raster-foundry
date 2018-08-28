import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M149 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(149)(
    List(
      sqlu"""
      INSERT INTO feature_flags (id, name, key, active, description) values (
        uuid_generate_v4(),
        'Project list sharing filter',
        'project-list-sharing-filter',
        false,
        'Allow users to switch between their own projects and projects that have been shared with them'
      );
    """
    ))
}

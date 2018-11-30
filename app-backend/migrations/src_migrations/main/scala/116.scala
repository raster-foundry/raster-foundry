import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M116 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(116)(
    List(
      sqlu"""
      ALTER TABLE access_control_rules ALTER COLUMN action_type TYPE varchar(255);

      DROP TYPE action_type;

      CREATE TYPE action_type AS ENUM ('VIEW', 'EDIT', 'DEACTIVATE', 'DELETE', 'ANNOTATE', 'DOWNLOAD', 'EXPORT');

      ALTER TABLE access_control_rules ALTER COLUMN action_type TYPE action_type using action_type::action_type;
    """
    ))
}

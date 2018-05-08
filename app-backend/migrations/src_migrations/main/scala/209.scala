import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M209 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(209)(List(
    sqlu"""
      ALTER TABLE access_control_rules ALTER COLUMN action_type TYPE varchar(255);

      DROP TYPE action_type;

      CREATE TYPE action_type AS ENUM ('VIEW', 'EDIT', 'DEACTIVATE', 'DELETE', 'ANNOTATE');

      ALTER TABLE access_control_rules ALTER COLUMN action_type TYPE action_type using action_type::action_type;
    """
  ))
}

import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M94 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(94)(
    List(
      sqlu"""
        ALTER TABLE tools
        DROP CONSTRAINT tools_unique_constraint;
    """
    ))
}

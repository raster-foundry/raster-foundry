import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M210 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(210)(List(
    sqlu"""
      ALTER TABLE platforms ADD COLUMN default_organization_id UUID REFERENCES organizations(id);
    """
  ))
}

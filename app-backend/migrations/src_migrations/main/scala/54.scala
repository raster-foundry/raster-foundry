import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M54 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(54)(
    List(
      sqlu"""
    CREATE TABLE organization_features (
      organization UUID REFERENCES organizations (id),
      feature_flag UUID REFERENCES feature_flags (id),
      active BOOLEAN NOT NULL DEFAULT FALSE,
      PRIMARY KEY (organization, feature_flag)
    );
    """
    ))
}

import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M96 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(96)(
    List(
      sqlu"""
    INSERT INTO feature_flags (id, key, active, name, description) VALUES
      (
        '828c77c9-49e8-4a99-9814-169300ec1b88',
        'user-vectors',
        FALSE,
        'Vector data integration',
        'Support user defined vectors'
      );
"""
    ))
}

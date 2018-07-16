import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M139 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(139)(List(
    sqlu"""
      INSERT INTO feature_flags (
        id, key, active, name, description
      ) VALUES (
        'dd486e2d-de6c-4ea6-afbe-cd0a2a2fa698',
        'project-preview-mini-map',
        true,
        'Project preview mini leaflet map',
        'Make Project Previews mini Leaflet Maps'
      );
    """
  ))
}

import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M89 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(89)(
    List(
      sqlu"""

      CREATE TABLE shapes (
        id UUID PRIMARY KEY NOT NULL,
        created_at TIMESTAMP NOT NULL,
        created_by VARCHAR(255) REFERENCES users(id) NOT NULL,
        modified_at TIMESTAMP NOT NULL,
        modified_by VARCHAR(255) REFERENCES users(id) NOT NULL,
        owner VARCHAR(255) NULL,
        organization_id UUID REFERENCES organizations(id) NOT NULL,
        name TEXT NOT NULL,
        description TEXT
      );

      -- Add a generic geometry column named 'geometry' in 3857 with 2 dimensions
      SELECT AddGeometryColumn('shapes', 'geometry', 3857, 'GEOMETRY', 2);
    """
    ))
}

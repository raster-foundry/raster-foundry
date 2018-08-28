import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M86 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(86)(
    List(
      sqlu"""
      CREATE TYPE annotation_quality AS ENUM('YES', 'NO', 'MISS', 'UNSURE');

      CREATE TABLE annotations (
        id UUID PRIMARY KEY NOT NULL,
        project_id UUID REFERENCES projects(id) NOT NULL,
        created_at TIMESTAMP NOT NULL,
        created_by VARCHAR(255) REFERENCES users(id) NOT NULL,
        modified_at TIMESTAMP NOT NULL,
        modified_by VARCHAR(255) REFERENCES users(id) NOT NULL,
        owner VARCHAR(255) NULL,
        organization_id UUID REFERENCES organizations(id) NOT NULL,
        label TEXT NOT NULL,
        description TEXT,
        machine_generated BOOLEAN DEFAULT FALSE,

        -- the REAL type has at least 6 digits of precision
        confidence REAL,

        -- YES, NO, MISS, UNSURE
        quality annotation_quality
      );

      -- Add a generic geometry column named 'geometry' in 3857 with 2 dimensions
      SELECT AddGeometryColumn('annotations', 'geometry', 3857, 'GEOMETRY', 2);
    """
    ))
}

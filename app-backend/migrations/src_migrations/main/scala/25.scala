import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M25 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(25)(
    List(sqlu"""
       CREATE TABLE model_tags (
         id UUID PRIMARY KEY NOT NULL,
         created_at TIMESTAMP NOT NULL,
         modified_at TIMESTAMP NOT NULL,
         created_by VARCHAR(255) REFERENCES users(id) NOT NULL,
         modified_by VARCHAR(255) REFERENCES users(id) NOT NULL,
         organization_id UUID REFERENCES organizations(id) NOT NULL,
         tag VARCHAR(255) NOT NULL
       );

       ALTER TABLE
         model_tags
       ADD CONSTRAINT
         model_tags_unique_constraint
       UNIQUE (organization_id, tag);

    """))
}

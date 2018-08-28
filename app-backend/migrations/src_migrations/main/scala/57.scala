import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M57 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(57)(
    List(
      sqlu"""
ALTER TABLE aois ADD COLUMN owner VARCHAR(255) NULL;
ALTER TABLE datasources ADD COLUMN owner VARCHAR(255) NULL;
ALTER TABLE exports ADD COLUMN owner VARCHAR(255) NULL;
ALTER TABLE images ADD COLUMN owner VARCHAR(255) NULL;
ALTER TABLE map_tokens ADD COLUMN owner VARCHAR(255) NULL;
ALTER TABLE projects ADD COLUMN owner VARCHAR(255) NULL;
ALTER TABLE scenes ADD COLUMN owner VARCHAR(255) NULL;
ALTER TABLE tool_runs ADD COLUMN owner VARCHAR(255) NULL;
ALTER TABLE tools ADD COLUMN owner VARCHAR(255) NULL;
ALTER TABLE tool_tags ADD COLUMN owner VARCHAR(255) NULL;
ALTER TABLE uploads ADD COLUMN owner VARCHAR(255) NULL;

UPDATE aois SET owner = created_by;
UPDATE datasources SET owner = created_by;
UPDATE exports SET owner = created_by;
UPDATE images SET owner = created_by;
UPDATE map_tokens SET owner = created_by;
UPDATE projects SET owner = created_by;
UPDATE scenes SET owner = created_by;
UPDATE tool_runs SET owner = created_by;
UPDATE tools SET owner = created_by;
UPDATE tool_tags SET owner = created_by;
UPDATE uploads SET owner = created_by;

ALTER TABLE aois ALTER COLUMN owner SET NOT NULL;
ALTER TABLE datasources ALTER COLUMN owner SET NOT NULL;
ALTER TABLE exports ALTER COLUMN owner SET NOT NULL;
ALTER TABLE images ALTER COLUMN owner SET NOT NULL;
ALTER TABLE map_tokens ALTER COLUMN owner SET NOT NULL;
ALTER TABLE projects ALTER COLUMN owner SET NOT NULL;
ALTER TABLE scenes ALTER COLUMN owner SET NOT NULL;
ALTER TABLE tool_runs ALTER COLUMN owner SET NOT NULL;
ALTER TABLE tools ALTER COLUMN owner SET NOT NULL;
ALTER TABLE tool_tags ALTER COLUMN owner SET NOT NULL;
ALTER TABLE uploads ALTER COLUMN owner SET NOT NULL;

ALTER TABLE aois ADD CONSTRAINT aois_owner_fkey FOREIGN KEY (owner) REFERENCES users(id) NOT VALID;
ALTER TABLE datasources ADD CONSTRAINT datasources_owner_fkey FOREIGN KEY (owner) REFERENCES users(id) NOT VALID;
ALTER TABLE exports ADD CONSTRAINT exports_owner_fkey FOREIGN KEY (owner) REFERENCES users(id) NOT VALID;
ALTER TABLE images ADD CONSTRAINT images_owner_fkey FOREIGN KEY (owner) REFERENCES users(id) NOT VALID;
ALTER TABLE map_tokens ADD CONSTRAINT map_tokens_owner_fkey FOREIGN KEY (owner) REFERENCES users(id) NOT VALID;
ALTER TABLE projects ADD CONSTRAINT projects_owner_fkey FOREIGN KEY (owner) REFERENCES users(id) NOT VALID;
ALTER TABLE scenes ADD CONSTRAINT scenes_owner_fkey FOREIGN KEY (owner) REFERENCES users(id) NOT VALID;
ALTER TABLE tool_runs ADD CONSTRAINT tool_runs_owner_fkey FOREIGN KEY (owner) REFERENCES users(id) NOT VALID;
ALTER TABLE tools ADD CONSTRAINT tools_owner_fkey FOREIGN KEY (owner) REFERENCES users(id) NOT VALID;
ALTER TABLE tool_tags ADD CONSTRAINT tool_tags_owner_fkey FOREIGN KEY (owner) REFERENCES users(id) NOT VALID;
ALTER TABLE uploads ADD CONSTRAINT uploads_owner_fkey FOREIGN KEY (owner) REFERENCES users(id) NOT VALID;

UPDATE users SET organization_id = '9e2bef18-3f46-426b-a5bd-9913ee1ff840' where id = 'rf|airflow-user';
"""
    ))
}

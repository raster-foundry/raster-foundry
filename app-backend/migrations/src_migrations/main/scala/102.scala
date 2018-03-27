import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M102 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(102)(List(
    sqlu"""
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

ALTER TABLE tool_runs RENAME TO analyses;
ALTER TABLE analyses ADD COLUMN readonly BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE templates (
  id UUID PRIMARY KEY NOT NULL,
  created_at TIMESTAMP NOT NULL,
  modified_at TIMESTAMP NOT NULL,
  created_by VARCHAR(255) REFERENCES users(id) NOT NULL,
  modified_by VARCHAR(255) REFERENCES users(id) NOT NULL,
  owner VARCHAR(255) REFERENCES users(id) NOT NULL,
  organization_id UUID REFERENCES organizations(id) NOT NULL,
  name VARCHAR(255) NOT NULL,
  details TEXT NOT NULL DEFAULT '',
  description TEXT NOT NULL DEFAULT '',
  thumbnail_url TEXT NOT NULL DEFAULT '',
  requirements TEXT NOT NULL DEFAULT '',
  compatible_data_sources TEXT[] NOT NULL DEFAULT array[]::text[],
  license VARCHAR(255) REFERENCES licenses(short_name),
  visibility VISIBILITY NOT NULL
);

CREATE TABLE template_versions (
  id BIGSERIAL PRIMARY KEY NOT NULL,
  created_at TIMESTAMP NOT NULL,
  created_by VARCHAR(255) NOT NULL REFERENCES users(id),
  modified_at TIMESTAMP NOT NULL,
  modified_by VARCHAR(255) NOT NULL REFERENCES users(id),
  version VARCHAR(140) NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  changelog TEXT NOT NULL DEFAULT '',
  template_id UUID REFERENCES templates(id) NOT NULL,
  analysis_id UUID REFERENCES analyses(id) NOT NULL
);

ALTER TABLE tool_tags RENAME TO tags;
ALTER TABLE tool_categories RENAME TO categories;

CREATE TABLE template_tags (
  template_id UUID REFERENCES templates(id) NOT NULL,
  tag_id UUID REFERENCES tags(id) NOT NULL
);

CREATE TABLE template_categories (
  template_id UUID REFERENCES templates(id) NOT NULL,
  category_slug VARCHAR(255) REFERENCES categories(slug_label) NOT NULL
);

-- create workspaces for every analysis

CREATE TABLE workspaces (
  id UUID PRIMARY KEY NOT NULL,
  created_at TIMESTAMP NOT NULL,
  modified_at TIMESTAMP NOT NULL,
  created_by VARCHAR(255) REFERENCES users(id) NOT NULL,
  modified_by VARCHAR(255) REFERENCES users(id) NOT NULL,
  owner VARCHAR(255) REFERENCES users(id) NOT NULL,
  organization_id UUID REFERENCES organizations(id) NOT NULL,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  active_analysis UUID REFERENCES analyses
);

CREATE TABLE workspace_tags (
  workspace_id UUID REFERENCES workspaces(id) NOT NULL,
  tag_id UUID REFERENCES tags(id) NOT NULL
);

CREATE TABLE workspace_categories (
  workspace_id UUID REFERENCES workspaces(id) NOT NULL,
  category_slug VARCHAR(255) REFERENCES categories(slug_label) NOT NULL
);

CREATE TABLE workspace_analyses (
  workspace UUID REFERENCES workspaces(id) NOT NULL,
  analysis UUID REFERENCES analyses(id) NOT NULL
);

-- Create an analysis from every tool, then link it to a template as a version
INSERT INTO analyses (
  id, created_at, created_by, modified_at, modified_by, visibility, organization_id,
  execution_parameters, owner, name, readonly
) SELECT
  t.id, t.created_at, t.created_by, t.modified_at, t.modified_by, t.visibility,
  t.organization_id, t.definition, t.owner, t.title, true
FROM tools t;

INSERT INTO templates (
  id, created_at, created_by, modified_at, modified_by, owner, visibility, organization_id,
  name, description, requirements
) select
  t.id, t.created_at, t.created_by, t.modified_at, t.modified_by, t.owner,
  t.visibility, t.organization_id, t.title, t.description,
  t.requirements
FROM tools t;

INSERT INTO template_versions(
  created_at, created_by, modified_at, modified_by, version, template_id, analysis_id
)
SELECT t.created_at, t.created_by, t.modified_at, t.modified_by, '1.0', t.id, t.id
FROM tools t;

-- Move tool tags over to templates
  INSERT INTO template_tags (
    template_id,
    tag_id
  )
  SELECT tv.template_id, tt.tool_tag_id
  FROM (
    SELECT template_id, analysis_id FROM template_versions
  ) tv
  JOIN tool_tags_to_tools tt ON tv.analysis_id = tt.tool_id;

  DROP table tool_tags_to_tools;

-- Move tool categories over to templates
  INSERT INTO template_categories (
    template_id,
    category_slug
  )
  SELECT tv.template_id, tc.tool_category_slug
  FROM (
    SELECT template_id, analysis_id FROM template_versions
  ) tv
  JOIN tool_categories_to_tools tc ON tv.analysis_id = tc.tool_id;

  DROP TABLE tool_categories_to_tools;

-- delete tool table - no longer needed
DROP TABLE tools;
"""
  ))
}

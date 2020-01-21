ALTER TABLE annotation_projects
  ALTER COLUMN labelers_team_id DROP NOT NULL,
  ALTER COLUMN validators_team_id DROP NOT NULL,
  ALTER COLUMN organization_id DROP NOT NULL;

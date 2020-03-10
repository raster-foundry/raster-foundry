-- add index for owner lookup, since we'll have to do it _all the time_
CREATE INDEX CONCURRENTLY IF NOT EXISTS annotation_projects_owner_idx
  ON annotation_projects (owner);

-- we'll search by name in the Groundwork UI
CREATE INDEX CONCURRENTLY IF NOT EXISTS annotation_projects_name_idx
  ON annotation_projects (name);

-- common strategy from other tables to make permissions lookups fast
CREATE INDEX CONCURRENTLY IF NOT EXISTS annotation_projects_acrs_idx
  ON annotation_projects USING GIN (acrs);

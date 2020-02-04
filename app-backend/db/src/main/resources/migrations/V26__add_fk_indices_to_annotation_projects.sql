-- add index for owner lookup, since we'll have to do it _all the time_
CREATE INDEX CONCURRENTLY IF NOT EXISTS annotation_projects_owner_idx
  ON annotation_projects (owner);

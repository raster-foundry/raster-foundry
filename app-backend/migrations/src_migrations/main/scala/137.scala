import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M137 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(137)(
    List(
      sqlu"""
CREATE TABLE annotation_groups (
  id            UUID PRIMARY KEY NOT NULL,
  name          TEXT NOT NULL,
  created_at    TIMESTAMP NOT NULL,
  created_by    VARCHAR(255) references users(id) NOT NULL,
  modified_at   TIMESTAMP NOT NULL,
  modified_by    VARCHAR(255) references users(id) NOT NULL,
  project_id    UUID REFERENCES projects(id) NOT NULL,
  default_style JSONB
);

ALTER TABLE projects ADD column default_annotation_group UUID references annotation_groups(id);

INSERT INTO annotation_groups (id, name, created_at, created_by, modified_at, modified_by, project_id)
SELECT uuid_generate_v4(), 'Annotations', now(), x.created_by, now(), x.created_by, x.id
FROM
(
  SELECT p.id as id, count(annotations.id) as count, p.created_by as created_by
  FROM projects p JOIN annotations ON p.id = annotations.project_id
  GROUP BY p.id
) x
WHERE x.count > 0;

UPDATE projects set default_annotation_group = annotation_groups.id
FROM annotation_groups
WHERE projects.id = annotation_groups.project_id;

ALTER TABLE annotations ADD COLUMN annotation_group UUID references annotation_groups(id);

UPDATE annotations SET annotation_group = annotation_groups.id
FROM annotation_groups
WHERE annotations.project_id = annotation_groups.project_id;

ALTER TABLE annotations ALTER COLUMN annotation_group SET NOT NULL;
"""
    ))
}

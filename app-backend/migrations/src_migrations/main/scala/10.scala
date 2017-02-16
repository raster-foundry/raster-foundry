import slick.driver.PostgresDriver.api._
import com.liyaos.forklift.slick.SqlMigration

object M10 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(10)(List(
    sqlu"""
CREATE TABLE buckets (
  id UUID PRIMARY KEY NOT NULL,
  created_at TIMESTAMP NOT NULL,
  modified_at TIMESTAMP NOT NULL,
  organization_id UUID REFERENCES organizations(id) NOT NULL,
  created_by VARCHAR(255) REFERENCES users(id) NOT NULL,
  modified_by VARCHAR(255) REFERENCES users(id) NOT NULL,
  name TEXT NOT NULL,
  slug_label VARCHAR(255) NOT NULL,
  description TEXT NOT NULL,
  visibility visibility NOT NULL,
  tags text[]
);

CREATE TABLE scenes_to_buckets (
        scene_id UUID REFERENCES scenes(id),
        bucket_id UUID REFERENCES buckets(id),
        CONSTRAINT scenes_to_buckets_pkey PRIMARY KEY (scene_id, bucket_id)
);

"""
  ))
}

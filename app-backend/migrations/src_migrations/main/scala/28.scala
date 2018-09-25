import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M28 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(28)(
    List(
      sqlu"""
ALTER TABLE model_categories RENAME TO tool_categories;

ALTER TABLE tool_categories RENAME CONSTRAINT
  model_categories_unique_constraint TO tool_categories_unique_constraint;
ALTER TABLE tool_categories RENAME CONSTRAINT
  model_categories_pkey TO tool_categories_pkey;
ALTER TABLE tool_categories RENAME CONSTRAINT
  model_categories_created_by_fkey TO tool_categories_created_by_fkey;
ALTER TABLE tool_categories RENAME CONSTRAINT
  model_categories_modified_by_fkey TO tool_categories_modified_by_fkey;


ALTER TABLE model_tags RENAME TO tool_tags;

ALTER TABLE tool_tags RENAME CONSTRAINT
  model_tags_pkey TO tool_tags_pkey;
ALTER TABLE tool_tags RENAME CONSTRAINT
  model_tags_unique_constraint TO tool_tags_unique_constraint;
ALTER TABLE tool_tags RENAME CONSTRAINT
  model_tags_organization_id_fkey TO tool_tags_organization_id_fkey;
ALTER TABLE tool_tags RENAME CONSTRAINT
  model_tags_created_by_fkey TO tool_tags_created_by_fkey;
ALTER TABLE tool_tags RENAME CONSTRAINT
  model_tags_modified_by_fkey TO tool_tags_modified_by_fkey;
""" // your sql code goes here
    ))
}

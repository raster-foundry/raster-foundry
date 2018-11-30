import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M31 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(31)(
    List(
      sqlu"""
TRUNCATE tool_categories CASCADE;
ALTER TABLE tool_categories ADD COLUMN  slug_label VARCHAR(255) NOT NULL;

ALTER TABLE tool_categories_to_tools DROP CONSTRAINT tool_categories_to_tools_pkey;
ALTER TABLE tool_categories_to_tools DROP CONSTRAINT tool_categories_to_tools_tool_category_id_fkey;
ALTER TABLE tool_categories_to_tools DROP COLUMN tool_category_id;

ALTER TABLE tool_categories DROP CONSTRAINT tool_categories_pkey;
ALTER TABLE tool_categories ADD PRIMARY KEY (slug_label);
ALTER TABLE tool_categories DROP COLUMN id;

ALTER TABLE tool_categories_to_tools
  ADD COLUMN tool_category_slug VARCHAR(255)
    REFERENCES tool_categories(slug_label);
ALTER TABLE tool_categories_to_tools
  DROP CONSTRAINT tool_categories_to_tools_tool_category_slug_fkey,
  ADD CONSTRAINT tool_categories_to_tools_tool_category_flug_fkey
    FOREIGN KEY (tool_category_slug)
    REFERENCES tool_categories(slug_label)
    ON DELETE CASCADE;
ALTER TABLE tool_categories_to_tools
  ADD PRIMARY KEY (tool_category_slug, tool_id)
"""
    ))
}

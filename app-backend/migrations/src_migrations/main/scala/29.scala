import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M29 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(29)(
    List(
      sqlu"""
       CREATE TABLE tools (
         id UUID PRIMARY KEY NOT NULL,
         created_at TIMESTAMP NOT NULL,
         modified_at TIMESTAMP NOT NULL,
         created_by VARCHAR(255) REFERENCES users(id) NOT NULL,
         modified_by VARCHAR(255) REFERENCES users(id) NOT NULL,
         organization_id UUID REFERENCES organizations(id) NOT NULL,
         title VARCHAR(255) NOT NULL,
         description TEXT NOT NULL,
         requirements TEXT NOT NULL,
         license TEXT NOT NULL,
         visibility visibility NOT NULL,
         compatible_data_sources text[] DEFAULT '{}' NOT NULL,
         stars REAL DEFAULT 0.0 NOT NULL
       );

       ALTER TABLE
         tools
       ADD CONSTRAINT
         tools_unique_constraint
       UNIQUE (organization_id, title);

      CREATE TABLE tool_tags_to_tools (
        tool_tag_id UUID REFERENCES tool_tags(id),
        tool_id UUID REFERENCES tools(id),
        CONSTRAINT tool_tags_to_tools_pkey PRIMARY KEY (tool_tag_id, tool_id)
      );

      ALTER TABLE tool_tags_to_tools
        DROP CONSTRAINT tool_tags_to_tools_tool_id_fkey,
        ADD CONSTRAINT tool_tags_to_tools_tool_id_fkey
          FOREIGN KEY (tool_id)
          REFERENCES tools(id)
          ON DELETE CASCADE;

      ALTER TABLE tool_tags_to_tools
        DROP CONSTRAINT tool_tags_to_tools_tool_tag_id_fkey,
        ADD CONSTRAINT tool_tags_to_tools_tool_tag_id_fkey
          FOREIGN KEY (tool_tag_id)
          REFERENCES tool_tags(id)
          ON DELETE CASCADE;

      CREATE TABLE tool_categories_to_tools (
        tool_category_id UUID REFERENCES tool_categories(id),
        tool_id UUID REFERENCES tools(id),
        CONSTRAINT tool_categories_to_tools_pkey PRIMARY KEY (tool_category_id, tool_id)
      );

      ALTER TABLE tool_categories_to_tools
        DROP CONSTRAINT tool_categories_to_tools_tool_id_fkey,
        ADD CONSTRAINT tool_categories_to_tools_tool_id_fkey
          FOREIGN KEY (tool_id)
          REFERENCES tools(id)
          ON DELETE CASCADE;

      ALTER TABLE tool_categories_to_tools
        DROP CONSTRAINT tool_categories_to_tools_tool_category_id_fkey,
        ADD CONSTRAINT tool_categories_to_tools_tool_category_id_fkey
          FOREIGN KEY (tool_category_id)
          REFERENCES tool_categories(id)
          ON DELETE CASCADE;
"""
    ))
}

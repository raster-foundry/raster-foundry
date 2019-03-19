import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M172 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(172)(
    List(
      sqlu"""
DROP TABLE tool_categories CASCADE;
DROP TABLE tool_categories_to_tools CASCADE;
DROP TABLE tool_tags_to_tools CASCADE;
DROP TABLE tool_tags CASCADE;
"""
    ))
}

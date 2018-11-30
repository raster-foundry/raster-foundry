import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M83 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(83)(
    List(
      sqlu"""
ALTER TABLE tool_runs ADD COLUMN name TEXT DEFAULT '';

UPDATE tool_runs
SET name = tools.title
FROM tools
WHERE tool_runs.tool = tools.id;

ALTER TABLE tool_runs DROP COLUMN tool;
""" // your sql code goes here
    ))
}

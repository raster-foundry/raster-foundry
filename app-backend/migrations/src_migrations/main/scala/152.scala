import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M152 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(152)(
    List(
      sqlu"""
    -- Delete some 'ALL' ACR records which we used visibility = 'PUBLIC' to populate in migration 118
    -- we will rely on visibility = 'PUBLIC' again in future commits in this anchored PR
    DELETE FROM access_control_rules
    WHERE subject_type = 'ALL' AND
    (
      (object_type = 'SCENE' AND (action_type = 'VIEW' OR action_type = 'DOWNLOAD'))
      OR
      (object_type = 'PROJECT' AND (action_type = 'VIEW' OR action_type = 'EXPORT' OR action_type = 'ANNOTATE'))
      OR
      (object_type= 'DATASOURCE' AND action_type = 'VIEW')
      OR
      (object_type = 'TEMPLATE' AND action_type = 'VIEW')
      OR
      (object_type = 'ANALYSIS' AND (action_type = 'VIEW' OR action_type = 'EXPORT'))
    );
     -- Add acrs column as text array in first class object table
    ALTER TABLE scenes ADD COLUMN acrs text[] NOT NULL DEFAULT '{}';
    ALTER TABLE projects ADD COLUMN acrs text[] NOT NULL DEFAULT '{}';
    ALTER TABLE datasources ADD COLUMN acrs text[] NOT NULL DEFAULT '{}';
    ALTER TABLE tools ADD COLUMN acrs text[] NOT NULL DEFAULT '{}';
    ALTER TABLE tool_runs ADD COLUMN acrs text[] NOT NULL DEFAULT '{}';
    ALTER TABLE shapes ADD COLUMN acrs text[] NOT NULL DEFAULT '{}';
     -- Add object level ACRs
    WITH scene_acrs AS (
       SELECT object_id,
              array_agg(concat_ws(';', subject_type, coalesce(subject_id, ''), action_type)) AS acrs
       FROM access_control_rules WHERE object_type = 'SCENE' GROUP BY object_id
    )
    UPDATE scenes
    SET acrs = scene_acrs.acrs
    FROM scene_acrs
    WHERE scene_acrs.object_id = scenes.id;
     WITH project_acrs AS (
       SELECT object_id,
              array_agg(concat_ws(';', subject_type, coalesce(subject_id, ''), action_type)) AS acrs
       FROM access_control_rules WHERE object_type = 'PROJECT' GROUP BY object_id
    )
    UPDATE projects
    SET acrs = project_acrs.acrs
    FROM project_acrs
    WHERE project_acrs.object_id = projects.id;
     WITH datasource_acrs AS (
       SELECT object_id,
              array_agg(concat_ws(';', subject_type, coalesce(subject_id, ''), action_type)) AS acrs
       FROM access_control_rules WHERE object_type = 'DATASOURCE'  GROUP BY object_id
    )
    UPDATE datasources
    SET acrs = datasource_acrs.acrs
    FROM datasource_acrs
    WHERE datasource_acrs.object_id = datasources.id;
     WITH tool_run_acrs AS (
       SELECT object_id,
              array_agg(concat_ws(';', subject_type, coalesce(subject_id, ''), action_type)) AS acrs
       FROM access_control_rules WHERE object_type = 'ANALYSIS' GROUP BY object_id
    )
    UPDATE tool_runs
    SET acrs = tool_run_acrs.acrs
    FROM tool_run_acrs
    WHERE tool_run_acrs.object_id = tool_runs.id;
     WITH tool_acrs AS (
       SELECT object_id,
              array_agg(concat_ws(';', subject_type, coalesce(subject_id, ''), action_type)) AS acrs
       FROM access_control_rules WHERE object_type = 'TEMPLATE' GROUP BY object_id
    )
    UPDATE tools
    SET acrs = tool_acrs.acrs
    FROM tool_acrs
    WHERE tool_acrs.object_id = tools.id;
     WITH shape_acrs AS (
       SELECT object_id,
              array_agg(concat_ws(';', subject_type, coalesce(subject_id, ''), action_type)) AS acrs
       FROM access_control_rules WHERE object_type = 'SHAPE' GROUP BY object_id
    )
    UPDATE shapes
    SET acrs = shape_acrs.acrs
    FROM shape_acrs
    WHERE shape_acrs.object_id = shapes.id;
     -- Create Index
    CREATE INDEX IF NOT EXISTS scenes_acrs on "scenes" USING GIN ("acrs");
    CREATE INDEX IF NOT EXISTS projects_acrs on "projects" USING GIN ("acrs");
    CREATE INDEX IF NOT EXISTS datasources_acrs on "datasources" USING GIN ("acrs");
    CREATE INDEX IF NOT EXISTS tools_acrs on "tools" USING GIN ("acrs");
    CREATE INDEX IF NOT EXISTS tool_runs_acrs on "tool_runs" USING GIN ("acrs");
    CREATE INDEX IF NOT EXISTS shapes_acrs on "shapes" USING GIN ("acrs");
    """
    ))
}

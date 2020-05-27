-- update campaigns table to include children_count and project_statuses columns
ALTER TABLE public.campaigns
ADD COLUMN children_count INTEGER NOT NULL default 0,
ADD COLUMN project_statuses jsonb NOT NULL default '{"WAITING": 0, "QUEUED": 0, "PROCESSING": 0, "READY": 0, "UNKNOWN_FAILURE": 0, "TASK_GRID_FAILURE": 0, "IMAGE_INGESTION_FAILURE": 0}'::jsonb;

-- add indices
CREATE INDEX IF NOT EXISTS campaigns_children_count_idx
ON public.campaigns
USING btree (children_count);
CREATE INDEX IF NOT EXISTS campaigns_project_statuses_idx
ON public.campaigns
USING gin (project_statuses);

-- a function to construct status jsonb for campaign
CREATE OR REPLACE FUNCTION CREATE_CAMPAIGN_PROJECT_STATUSES (
  project_statuses jsonb
)
RETURNS jsonb AS
$func$
DECLARE
  st text;
  new_project_statuses jsonb := '{}'::jsonb;
BEGIN
  FOREACH st IN ARRAY enum_range(NULL::annotation_project_status)::text[] LOOP
    IF (project_statuses->>st) IS NULL THEN
      new_project_statuses := new_project_statuses || jsonb_build_object(st, 0);
    ELSE
      new_project_statuses := new_project_statuses || project_statuses;
    END IF;
  END LOOP;
  RETURN new_project_statuses;
END;
$func$
LANGUAGE 'plpgsql';

-- update children_count column for existing campaigns
UPDATE public.campaigns
SET children_count = campaign_children_count.children_count
FROM (
  SELECT c1.id, count(c2.id) as children_count
  FROM public.campaigns c1
  LEFT JOIN public.campaigns c2
  ON c1.id = c2.parent_campaign_id
  group by c1.id
) campaign_children_count
WHERE campaign_children_count.id = campaigns.id;

-- update project_statuses column for existing campaigns
UPDATE public.campaigns
SET project_statuses = annotation_project_statuses.project_status
FROM (
  SELECT
  statuses.campaign_id, 
  CREATE_CAMPAIGN_PROJECT_STATUSES(
    jsonb_object_agg(
      statuses.status,
      statuses.status_count
    )
  ) AS project_status
  FROM (
    SELECT status, campaign_id, COUNT(id) AS status_count
    FROM public.annotation_projects
    WHERE campaign_id IS NOT NULL
    GROUP BY status, campaign_id
  ) statuses
  GROUP BY statuses.campaign_id
) AS annotation_project_statuses
WHERE annotation_project_statuses.campaign_id = id;

-- define the trigger function to update children_count for campaigns
CREATE OR REPLACE FUNCTION UPDATE_CAMPAIGN_CHILDREN_COUNT()
  RETURNS trigger AS
$BODY$
DECLARE
  op_campaign_id uuid;
  surplus integer;
BEGIN
  -- the NEW variable holds row for INSERT/UPDATE operations
  -- the OLD variable holds row for DELETE operations
  -- store the parent campaign ID
  IF TG_OP = 'INSERT' THEN
    op_campaign_id := NEW.parent_campaign_id;
    surplus := 1;
  ELSE
    op_campaign_id := OLD.parent_campaign_id;
    surplus := -1;
  END IF;
  -- update children_count for the parent campaign
  IF op_campaign_id IS NOT NULL THEN
    UPDATE public.campaigns
    SET children_count = children_count + (surplus)
    WHERE id = op_campaign_id;
  END IF;

  IF TG_OP = 'INSERT' THEN
    RETURN NEW;
  ELSE
    RETURN OLD;
  END IF;
END;
$BODY$
LANGUAGE 'plpgsql';

-- define the trigger function to update project_statuses for campaigns
CREATE OR REPLACE FUNCTION UPDATE_CAMPAIGN_PROJECT_STATUSES()
  RETURNS trigger AS
$BODY$
DECLARE
  op_campaign_id uuid;
BEGIN
  -- the NEW variable holds row for INSERT/UPDATE operations
  -- the OLD variable holds row for DELETE operations
  -- store the campaign ID from the annotation project update
  IF TG_OP = 'UPDATE' THEN
    op_campaign_id := NEW.campaign_id;
  END IF;
  -- update status for the parent campaign
  IF op_campaign_id IS NOT NULL THEN
    UPDATE public.campaigns
    SET project_statuses = annotation_project_statuses.project_status
    FROM (
      SELECT
      CREATE_CAMPAIGN_PROJECT_STATUSES(
        jsonb_object_agg(
          statuses.status,
          statuses.status_count
        )
      ) AS project_status
      FROM (
        SELECT status, COUNT(id) AS status_count
        FROM public.annotation_projects
        WHERE campaign_id = op_campaign_id
        GROUP BY status
      ) statuses
    ) AS annotation_project_statuses
    WHERE id = op_campaign_id;
  END IF;
  -- result is ignored since this is an AFTER trigger
  RETURN NULL;
END;
$BODY$
LANGUAGE 'plpgsql';

-- add a trigger to INSERT OR DELETE operations on campaigns table
CREATE TRIGGER update_campaign_children_count
  BEFORE INSERT OR DELETE
  ON campaigns
  FOR EACH ROW
  EXECUTE PROCEDURE UPDATE_CAMPAIGN_CHILDREN_COUNT();

-- add a trigger to UPDATE operation on campaigns table
CREATE TRIGGER update_campaign_project_statuses
  AFTER UPDATE OF status
  ON annotation_projects
  FOR EACH ROW
  EXECUTE PROCEDURE UPDATE_CAMPAIGN_PROJECT_STATUSES();
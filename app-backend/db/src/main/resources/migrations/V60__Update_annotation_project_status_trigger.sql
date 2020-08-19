DROP TRIGGER IF EXISTS update_campaign_project_statuses 
ON annotation_projects;

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
  ELSE
    op_campaign_id := OLD.campaign_id;
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

CREATE TRIGGER update_campaign_project_statuses
  AFTER UPDATE OF status OR DELETE
  ON annotation_projects
  FOR EACH ROW
  EXECUTE PROCEDURE UPDATE_CAMPAIGN_PROJECT_STATUSES();
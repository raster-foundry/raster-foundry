-- add column to track task status of campaigns and populate data from existing
-- campaign projects

ALTER TABLE
  public.campaigns
ADD
  COLUMN task_status_summary jsonb DEFAULT '{"FLAGGED": 0, "INVALID": 0, "LABELED": 0, "UNLABELED": 0, "VALIDATED": 0, "LABELING_IN_PROGRESS": 0, "VALIDATION_IN_PROGRESS": 0}' :: jsonb NOT NULL;

UPDATE
    campaigns
SET
    task_status_summary = campaign_status.task_status_summary
FROM
    (
        SELECT
            campaign_id,
            json_object_agg(k, v) task_status_summary
        FROM
            (
                SELECT
                    annotation_projects.campaign_id,
                    task_summary.key k,
                    sum(task_summary.value :: integer) v
                FROM
                    annotation_projects
                    JOIN jsonb_each_text(annotation_projects.task_status_summary) task_summary ON true
                WHERE
                    campaign_id IS NOT NULL
                GROUP BY
                    campaign_id,
                    key
                ORDER BY
                    k
            ) campaign_summary
        GROUP BY
            campaign_id
    ) campaign_status
WHERE
    campaigns.id = campaign_status.campaign_id;

CREATE
OR REPLACE FUNCTION UPDATE_CAMPAIGN_TASK_STATUSES() RETURNS trigger AS $BODY$ DECLARE op_campaign_id uuid;

BEGIN -- the NEW variable holds row for INSERT/UPDATE operations
-- the OLD variable holds row for DELETE operations
-- store the campaign ID from the annotation project update
IF TG_OP = 'UPDATE'
OR TG_OP = 'INSERT' THEN op_campaign_id := NEW.campaign_id;

ELSE op_campaign_id := OLD.campaign_id;

END IF;

-- update status for the parent campaign
IF op_campaign_id IS NOT NULL THEN
UPDATE
  public.campaigns
SET
  task_status_summary = campaign_status.task_status_summary
FROM
  (
    SELECT
      campaign_id,
      json_object_agg(status_label, status_count) task_status_summary
    FROM
      (
        SELECT
          annotation_projects.campaign_id,
          task_summary.key status_label,
          sum(task_summary.value :: integer) status_count
        FROM
          annotation_projects
          JOIN jsonb_each_text(annotation_projects.task_status_summary) task_summary ON true
        WHERE
          campaign_id = op_campaign_id
        GROUP BY
          campaign_id,
          key
        ORDER BY
          status_label
      ) campaign_summary
    GROUP BY
      campaign_id
  ) campaign_status
WHERE
  id = op_campaign_id;

END IF;

-- result is ignored since this is an AFTER trigger
RETURN NULL;

END;

$BODY$ LANGUAGE 'plpgsql';

CREATE TRIGGER update_campaign_task_statuses
AFTER
UPDATE
  OF task_status_summary
  OR DELETE
  OR
INSERT
  ON annotation_projects FOR EACH ROW EXECUTE PROCEDURE UPDATE_CAMPAIGN_TASK_STATUSES();
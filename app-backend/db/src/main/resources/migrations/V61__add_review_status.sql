-- vote types for each label
CREATE TYPE public.label_vote_type AS ENUM (
  'PASS',
  'FAIL'
);

-- review status of each "label" task
CREATE TYPE public.review_status AS ENUM (
  'REVIEW_PENDING',
  'REVIEW_VALIDATED',
  'REVIEW_NEEDS_ATTENTION'
);

-- add a review_status colum to tasks table
ALTER TABLE public.tasks
  ADD COLUMN review_status review_status DEFAULT NULL;

-- empty jsonb value should be written down as '{}'
-- there are some task records written by wrong review values
UPDATE tasks
SET reviews = '{}'
WHERE reviews = '"{}"';

-- a function to return review status criteria by task id
CREATE OR REPLACE FUNCTION GET_TASK_REVIEW_STATUS_BY_ID (
  task_id uuid
)
RETURNS table (
  id uuid,
  has_empty_review boolean,
  has_down_vote boolean,
  no_down_vote boolean,
  is_validated boolean
) AS
$func$
BEGIN
  RETURN QUERY
    WITH validated_label_task AS (
      SELECT
        *
      FROM
        tasks t
      WHERE
        status = 'VALIDATED'
        AND t.id = task_id
    ),
    empty_reviews AS (
      SELECT
        t.id
      FROM
        tasks t
      WHERE
        parent_task_id = task_id
        AND reviews = '{}'
    ),
    review_votes AS (
      SELECT
        jsonb_extract_path_text(reviews.value, 'vote') AS vote
      FROM
        tasks t,
        jsonb_each(t.reviews) AS reviews
      WHERE
        parent_task_id = task_id
    )
    SELECT
      task_id AS id,
      EXISTS (SELECT * FROM empty_reviews) has_empty_review,
      EXISTS (SELECT * FROM review_votes WHERE vote = 'FAIL') has_down_vote,
      NOT EXISTS (SELECT * FROM review_votes WHERE vote = 'FAIL') no_down_vote,
      EXISTS (SELECT* FROM validated_label_task) is_validated;
END;
$func$
LANGUAGE 'plpgsql';

-- an initial update query to fill in the reviewStatus field
UPDATE tasks
SET review_status = CASE WHEN (review_task_checks.has_empty_review) THEN
    'REVIEW_PENDING'::review_status
  WHEN (review_task_checks.has_down_vote) THEN
    'REVIEW_NEEDS_ATTENTION'::review_status
  WHEN (review_task_checks.is_validated
    OR review_task_checks.no_down_vote) THEN
    'REVIEW_VALIDATED'::review_status
  ELSE
    NULL
  END
FROM
  (
    SELECT task_criteria.*
    FROM (
      SELECT DISTINCT parent_task_id AS id
      FROM tasks
      WHERE parent_task_id IS NOT NULL
    ) as tasks_to_update,
    GET_TASK_REVIEW_STATUS_BY_ID(tasks_to_update.id) AS task_criteria
  ) review_task_checks
WHERE tasks.id = review_task_checks.id;

-- Pending review:
-- if there is still any task with '{}' in the reviews field.
-- Needs attention:
-- if none of the review is '{}', and there is at least one "fail" vote
-- Validated
-- If none of the review is '{}', and there is no "fail" vote;
-- or, the label task itself is validated
CREATE OR REPLACE FUNCTION UPDATE_TASK_REVIEW_STATUS ()
  RETURNS TRIGGER
  AS $BODY$
DECLARE
  op_parent_task_id uuid;
  op_task_type task_type;
BEGIN
  IF TG_OP = 'UPDATE' OR TG_OP = 'INSERT' THEN
    op_parent_task_id := NEW.parent_task_id;
    op_task_type := NEW.task_type;
  ELSE
    op_parent_task_id := OLD.parent_task_id;
    op_task_type := OLD.task_type;
  END IF;
  IF op_parent_task_id IS NOT NULL AND op_task_type = 'REVIEW' THEN
    UPDATE tasks
    SET review_status = CASE WHEN (review_task_checks.has_empty_review) THEN
        'REVIEW_PENDING'::review_status
      WHEN (review_task_checks.has_down_vote) THEN
        'REVIEW_NEEDS_ATTENTION'::review_status
      WHEN (review_task_checks.is_validated
        OR review_task_checks.no_down_vote) THEN
        'REVIEW_VALIDATED'::review_status
      ELSE
        NULL
      END
    FROM (
      SELECT * from GET_TASK_REVIEW_STATUS_BY_ID(op_parent_task_id)
    ) AS review_task_checks
    WHERE review_task_checks.id = tasks.id;

  END IF;
  RETURN NULL;
END;
$BODY$
LANGUAGE 'plpgsql';

-- create a trigger for updating task review
CREATE TRIGGER update_task_review_status
  AFTER UPDATE OF reviews OR INSERT OR DELETE ON tasks
  FOR EACH ROW
  EXECUTE PROCEDURE UPDATE_TASK_REVIEW_STATUS ();

-- update a previous trigger on task summary
DROP TRIGGER IF EXISTS update_annotation_project_task_summary 
ON tasks;

CREATE TRIGGER update_annotation_project_task_summary
  AFTER UPDATE OF status OR INSERT OR DELETE
  ON tasks
  FOR EACH ROW
  EXECUTE PROCEDURE UPDATE_PROJECT_TASK_SUMMARY();
CREATE TYPE public.label_vote_type AS ENUM (
  'PASS',
  'FAIL'
);

CREATE TYPE public.review_status AS ENUM (
  'REVIEW_PENDING',
  'REVIEW_VALIDATED',
  'REVIEW_NEEDS_ATTENTION'
);

ALTER TABLE public.tasks
  ADD COLUMN review_status review_status DEFAULT NULL;

-- empty jsonb value should be written down as '{}'
-- there are some task records written by wrong review values

UPDATE
  tasks
SET
  reviews = '{}'
WHERE
  reviews = '"{}"';

-- TODO: add an initial update query to fill in the reviewStatus field
-- REVIEW_PENDING:
-- if any review task has '{}' as reviews
-- REVIEW_NEEDS_ATTENTION:
-- if there is one "FAIL" vote in a review
-- REVIEW_VALIDATED:
-- if the label task is validated, or if all review votes are "PASS"

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
    WITH review_task_checks AS (
      SELECT
        EXISTS (
          SELECT
            id
          FROM
            tasks
          WHERE
            parent_task_id = op_parent_task_id
            AND reviews = '{}') has_empty_review,
        EXISTS (
          SELECT
            *
          FROM (
            SELECT
              jsonb_extract_path_text(reviews.value, 'vote') AS vote
            FROM
              tasks t,
              jsonb_each(t.reviews) AS reviews
          WHERE
            parent_task_id = op_parent_task_id) AS a
        WHERE
          a.vote = 'FAIL') has_down_vote,
        EXISTS (
          SELECT
            *
          FROM
            tasks
          WHERE
            status = 'VALIDATED'
            AND id = op_parent_task_id) is_validated,
          NOT EXISTS (
            SELECT
              *
            FROM (
              SELECT
                jsonb_extract_path_text(reviews.value, 'vote') AS vote
            FROM
              tasks t,
              jsonb_each(t.reviews) AS reviews
            WHERE
              parent_task_id = op_parent_task_id) AS a
          WHERE
            a.vote = 'FAIL') no_down_vote)
    UPDATE
      tasks
    SET
      review_status = CASE WHEN (review_task_checks.has_empty_review) THEN
        'REVIEW_PENDING'
      WHEN (review_task_checks.has_down_vote) THEN
        'REVIEW_NEEDS_ATTENTION'
      WHEN (review_task_checks.is_validated
        OR review_task_checks.no_down_vote) THEN
        'REVIEW_VALIDATED'
      ELSE
        NULL
      WHERE
        id = op_parent_task_id;
      END IF;
    RETURN NULL;
END;
$BODY$
LANGUAGE 'plpgsql';

CREATE TRIGGER update_task_review_status
  AFTER INSERT OR UPDATE OR DELETE ON tasks
  FOR EACH ROW
  EXECUTE PROCEDURE UPDATE_TASK_REVIEW_STATUS ();


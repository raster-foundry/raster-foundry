CREATE OR REPLACE FUNCTION CREATE_TASK_SUMMARY (
  summary jsonb
)
RETURNS jsonb AS
$func$
DECLARE
  status text;
  new_summary jsonb := '{}'::jsonb;
BEGIN
  FOREACH status IN ARRAY enum_range(NULL::task_status)::text[] LOOP
    IF (summary->>status) IS NULL THEN
      new_summary := new_summary || jsonb_build_object(status, 0);
    ELSE
      new_summary := new_summary || summary;
    END IF;
  END LOOP;
  RETURN new_summary;
END;
$func$
LANGUAGE 'plpgsql';
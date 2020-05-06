ALTER TABLE task_actions
  ADD COLUMN note text;

ALTER TABLE task_actions ADD CONSTRAINT task_actions_note_not_null_when_flagged CHECK (
  (to_status = 'FLAGGED' AND length(note) is not NULL AND length(note) > 0)
  OR to_status <> 'FLAGGED'
);

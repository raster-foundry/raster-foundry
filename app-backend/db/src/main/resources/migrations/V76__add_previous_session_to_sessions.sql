ALTER TABLE
    task_sessions
ADD
    COLUMN previous_session_id uuid REFERENCES task_sessions (id);
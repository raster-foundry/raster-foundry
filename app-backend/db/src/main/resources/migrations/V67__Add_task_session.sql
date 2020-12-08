CREATE TYPE task_session_type AS ENUM (
    'LABEL_SESSION', 'VALIDATE_SESSION'
);

CREATE TABLE public.task_sessions (
    id uuid primary key,
    created_at timestamp without time zone NOT NULL,
    last_tick_at timestamp without time zone NOT NULL,
    completed_at timestamp without time zone,
    from_status task_status NOT NULL,
    to_status task_status,
    session_type task_session_type NOT NULL,
    user_id text references users (id) ON DELETE CASCADE NOT NULL,
    task_id uuid references tasks (id) ON DELETE CASCADE NOT NULL,
    note text
);

CREATE INDEX IF NOT EXISTS task_sessions_last_tick_at_completed_at_idx ON public.task_sessions USING btree (last_tick_at, completed_at);
CREATE INDEX IF NOT EXISTS task_sessions_task_id_idx ON public.task_sessions USING btree (task_id);
CREATE INDEX IF NOT EXISTS task_sessions_user_id_idx ON public.task_sessions USING btree (user_id);
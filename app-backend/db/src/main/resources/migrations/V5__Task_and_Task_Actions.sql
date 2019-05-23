-- Create tasks table

CREATE TYPE task_status AS ENUM (
    'UNLABELED', 'LABELING_IN_PROGRESS', 'LABELED', 'VALIDATION_IN_PROGRESS', 'VALIDATED'
);

CREATE TABLE tasks (
  id uuid primary key,
  created_at timestamp without time zone  not null,
  created_by text not null references users (id),
  modified_at timestamp without time zone not null,
  modified_by text not null,
  owner text not null references users (id),
  project_id uuid not null,
  project_layer_id uuid not null,
  status task_status not null,
  locked_by text references users (id),
  locked_on timestamp without time zone,
  geometry geometry(Geometry, 3857) not null,
  CONSTRAINT tasks_project_id_fkey FOREIGN KEY (project_id) REFERENCES projects (id)
    ON DELETE CASCADE,
  CONSTRAINT tasks_project_layer_id_fkey FOREIGN KEY (project_layer_id) REFERENCES project_layers(id)
    ON DELETE CASCADE
);

CREATE TABLE task_actions (
  task_id uuid references tasks (id) NOT NULL,
  user_id text references users (id) NOT NULL,
  timestamp timestamp without time zone not null,
  from_status task_status not null,
  to_status task_status not null
);

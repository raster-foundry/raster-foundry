CREATE TYPE async_job_status as enum ('ACCEPTED', 'FAILED', 'SUCCEEDED');

CREATE TABLE async_user_bulk_create (
    id uuid primary key,
    owner text references users (id) not null,
    input jsonb not null,
    status async_job_status not null,
    errors jsonb not null default '[]',
    results jsonb not null default '[]'
);

CREATE TABLE async_campaign_clone (
    id uuid primary key,
    owner text references users (id) not null,
    input jsonb not null,
    status async_job_status not null,
    errors jsonb not null default '[]',
    results jsonb not null default '[]'
);
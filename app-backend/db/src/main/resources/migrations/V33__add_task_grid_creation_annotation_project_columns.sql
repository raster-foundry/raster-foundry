ALTER TABLE annotation_projects
    ALTER COLUMN task_size_meters SET DATA TYPE numeric,
    ADD COLUMN task_size_pixels integer NOT NULL default -1,
    ADD COLUMN ready boolean NOT NULL default false;

ALTER TABLE uploads
    ADD COLUMN annotation_project_id uuid REFERENCES annotation_projects(id),
    ADD COLUMN generate_tasks boolean NOT NULL default false;

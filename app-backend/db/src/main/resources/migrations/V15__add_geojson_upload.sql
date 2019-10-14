CREATE TABLE geojson_uploads (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL ,
    created_by TEXT NOT NULL REFERENCES users (id),
    modified_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    upload_status public.upload_status NOT NULL,
    file_type public.file_type NOT NULL,
    upload_type public.upload_type NOT NULL,
    files TEXT[] NOT NULL DEFAULT '{}'::TEXT[],
    project_id UUID NOT NULL REFERENCES projects (id),
    project_layer_id UUID NOT NULL REFERENCES project_layers (id),
    annotation_group UUID NOT NULL REFERENCES annotation_groups (id),
    keep_files BOOLEAN NOT NULL
);

CREATE INDEX geojson_uploads_created_at_idx ON geojson_uploads USING btree (created_at);
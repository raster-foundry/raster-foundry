CREATE INDEX CONCURRENTLY images_scene_idx ON images (scene);

CREATE INDEX CONCURRENTLY bands_image_idx ON bands (image_id);

CREATE INDEX CONCURRENTLY thumbnails_scene_idx ON thumbnails (scene);
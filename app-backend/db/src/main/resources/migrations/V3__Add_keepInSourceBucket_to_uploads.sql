-- Add a keepInSourceBucket boolean field to uploads table
-- to indicate whether to use source tif bucket or not

ALTER TABLE uploads ADD COLUMN keep_in_source_bucket BOOLEAN DEFAULT false NOT NULL;

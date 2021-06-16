CREATE INDEX CONCURRENTLY IF NOT EXISTS annotation_labels_is_active_idx
ON public.annotation_labels
USING btree (is_active);

CREATE INDEX CONCURRENTLY IF NOT EXISTS annotation_labels_session_id_idx
ON public.annotation_labels
USING btree (session_id);
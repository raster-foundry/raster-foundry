CREATE TABLE public.user_intercom_conversations (
    user_id text NOT NULL references users (id) ON DELETE CASCADE UNIQUE,
    conversation_id text NOT NULL
);

CREATE INDEX IF NOT EXISTS user_intercom_conversations_user_id_idx ON public.user_intercom_conversations USING btree (user_id);
-- updated user scopes in the users table with new JSONB strings

ALTER TABLE public.users ALTER COLUMN scopes SET DEFAULT '""'::jsonb;


UPDATE public.users SET scopes = '""'::jsonb;


UPDATE
   public.users 
SET
   scopes = '"platforms:admin"'::jsonb 
WHERE
   id in 
   (
      SELECT
         DISTINCT user_id 
      FROM
         user_group_roles 
      WHERE
         group_type = 'PLATFORM' 
         AND group_role = 'ADMIN' 
         AND is_active = true 
         AND membership_status = 'APPROVED'
   )
   AND scopes = '""'::jsonb;


UPDATE
   public.users 
SET
   scopes = '"organizations:admin"'::jsonb 
WHERE
   id in 
   (
      SELECT
         DISTINCT user_id 
      FROM
         user_group_roles 
      WHERE
         group_type = 'ORGANIZATION' 
         AND group_role = 'ADMIN' 
         AND is_active = true 
         AND membership_status = 'APPROVED'
   )
   AND scopes = '""'::jsonb;


UPDATE
   public.users 
SET
   scopes = '"teams:admin"'::jsonb 
WHERE
   id in 
   (
      SELECT
         DISTINCT user_id 
      FROM
         user_group_roles 
      WHERE
         group_type = 'TEAM' 
         AND group_role = 'ADMIN' 
         AND is_active = true 
         AND membership_status = 'APPROVED'
   )
   AND scopes = '""'::jsonb;


UPDATE
   public.users 
SET
   scopes = '"users:member"'::jsonb 
WHERE
   id in 
   (
      SELECT
         DISTINCT user_id 
      FROM
         user_group_roles 
      WHERE
         group_role = 'MEMBER' 
         AND is_active = true 
         AND membership_status = 'APPROVED'
   )
   AND scopes = '""'::jsonb;
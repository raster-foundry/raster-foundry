--
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: rasterfoundry
--

INSERT INTO public.users (id, role, created_at, modified_at, dropbox_credential, planet_credential, email_notifications, email, name, profile_image_uri, is_superuser, is_active, visibility, personal_info) VALUES ('default_projects', 'VIEWER', '2019-05-03 14:15:41.207292', '2019-05-03 14:15:41.207292', '', '', false, '', '', '', false, true, 'PRIVATE', '{"email": "", "lastName": "", "firstName": "", "profileBio": "", "profileUrl": "", "phoneNumber": "", "profileWebsite": "", "organizationName": "", "organizationType": "OTHER", "emailNotifications": false, "organizationWebsite": ""}') ON CONFLICT DO NOTHING;
INSERT INTO public.users (id, role, created_at, modified_at, dropbox_credential, planet_credential, email_notifications, email, name, profile_image_uri, is_superuser, is_active, visibility, personal_info) VALUES ('default', 'VIEWER', '2019-05-03 14:15:41.207292', '2019-05-03 14:15:41.207292', '', '', false, '', '', '', false, true, 'PRIVATE', '{"email": "", "lastName": "", "firstName": "", "profileBio": "", "profileUrl": "", "phoneNumber": "", "profileWebsite": "", "organizationName": "", "organizationType": "OTHER", "emailNotifications": false, "organizationWebsite": ""}') ON CONFLICT DO NOTHING;

--
-- Data for Name: user_group_roles; Type: TABLE DATA; Schema: public; Owner: rasterfoundry
--

INSERT INTO public.user_group_roles (id, created_at, created_by, modified_at, is_active, user_id, group_type, group_id, group_role, membership_status) VALUES ('d119e18d-916e-4394-8cf3-7a0aa6dfb13a', '2019-05-03 14:15:41.207292', 'default', '2019-05-03 14:15:41.207292', true, 'default_projects', 'ORGANIZATION', 'dfac6307-b5ef-43f7-beda-b9f208bb7726', 'MEMBER', 'APPROVED') ON CONFLICT DO NOTHING;
INSERT INTO public.user_group_roles (id, created_at, created_by, modified_at, is_active, user_id, group_type, group_id, group_role, membership_status) VALUES ('20a6ad49-3df3-4125-a140-ed4d25696fe3', '2019-05-03 14:15:41.207292', 'default', '2019-05-03 14:15:41.207292', true, 'default', 'ORGANIZATION', 'dfac6307-b5ef-43f7-beda-b9f208bb7726', 'MEMBER', 'APPROVED') ON CONFLICT DO NOTHING;
INSERT INTO public.user_group_roles (id, created_at, created_by, modified_at, is_active, user_id, group_type, group_id, group_role, membership_status) VALUES ('16060dbc-f88c-430e-aeeb-fa47beea80fe', '2019-05-03 14:15:41.207292', 'default', '2019-05-03 14:15:41.207292', true, 'default_projects', 'PLATFORM', '31277626-968b-4e40-840b-559d9c67863c', 'MEMBER', 'APPROVED') ON CONFLICT DO NOTHING;
INSERT INTO public.user_group_roles (id, created_at, created_by, modified_at, is_active, user_id, group_type, group_id, group_role, membership_status) VALUES ('8979d9d0-0802-4f4b-904b-ff736ad580b0', '2019-05-03 14:15:41.207292', 'default', '2019-05-03 14:15:41.207292', true, 'default', 'PLATFORM', '31277626-968b-4e40-840b-559d9c67863c', 'MEMBER', 'APPROVED') ON CONFLICT DO NOTHING;

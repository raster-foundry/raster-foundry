-- Disable triggers to allow creating circular reference
SET session_replication_role = replica;

--
-- Data for Name: organizations; Type: TABLE DATA; Schema: public; Owner: rasterfoundry
--

INSERT INTO public.organizations (id, created_at, modified_at, name, platform_id, dropbox_credential, planet_credential, logo_uri, visibility, status) VALUES ('9e2bef18-3f46-426b-a5bd-9913ee1ff840', '2019-05-03 14:15:41.207292', '2019-05-03 14:15:41.207292', 'root organization', '31277626-968b-4e40-840b-559d9c67863c', '', '', '', 'PRIVATE', 'ACTIVE') ON CONFLICT DO NOTHING;
INSERT INTO public.organizations (id, created_at, modified_at, name, platform_id, dropbox_credential, planet_credential, logo_uri, visibility, status) VALUES ('dfac6307-b5ef-43f7-beda-b9f208bb7726', '2019-05-03 14:15:41.207292', '2019-05-03 14:15:41.207292', 'Public', '31277626-968b-4e40-840b-559d9c67863c', '', '', '', 'PRIVATE', 'ACTIVE') ON CONFLICT DO NOTHING;

--
-- Data for Name: platforms; Type: TABLE DATA; Schema: public; Owner: rasterfoundry
--

INSERT INTO public.platforms (id, name, public_settings, is_active, default_organization_id, private_settings) VALUES ('31277626-968b-4e40-840b-559d9c67863c', 'Raster Foundry', '{"emailFrom": "noreply@example.com", "emailSupport": "support@example.com", "platformHost": null, "emailSmtpHost": "", "emailSmtpPort": 465, "emailSmtpUserName": "", "emailSmtpEncryption": "ssl", "emailAoiNotification": false, "emailFromDisplayName": "", "emailExportNotification": false, "emailIngestNotification": false}', true, NULL, '{"emailPassword": ""}') ON CONFLICT DO NOTHING;

-- Re-enable triggers and regenerate relevant indexes
SET session_replication_role = DEFAULT;

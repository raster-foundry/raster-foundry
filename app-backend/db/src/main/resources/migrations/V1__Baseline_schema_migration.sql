--
-- PostgreSQL database dump
--

-- Dumped from database version 9.6.8
-- Dumped by pg_dump version 11.2

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: postgis; Type: EXTENSION; Schema: -; Owner: 
--

CREATE EXTENSION IF NOT EXISTS postgis WITH SCHEMA public;


--
-- Name: EXTENSION postgis; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION postgis IS 'PostGIS geometry, geography, and raster spatial types and functions';


--
-- Name: uuid-ossp; Type: EXTENSION; Schema: -; Owner: 
--

CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;


--
-- Name: EXTENSION "uuid-ossp"; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION "uuid-ossp" IS 'generate universally unique identifiers (UUIDs)';


--
-- Name: action_type; Type: TYPE; Schema: public; Owner: rasterfoundry
--

CREATE TYPE public.action_type AS ENUM (
    'VIEW',
    'EDIT',
    'DEACTIVATE',
    'DELETE',
    'ANNOTATE',
    'DOWNLOAD',
    'EXPORT'
);


ALTER TYPE public.action_type OWNER TO rasterfoundry;

--
-- Name: annotation_quality; Type: TYPE; Schema: public; Owner: rasterfoundry
--

CREATE TYPE public.annotation_quality AS ENUM (
    'YES',
    'NO',
    'MISS',
    'UNSURE'
);


ALTER TYPE public.annotation_quality OWNER TO rasterfoundry;

--
-- Name: export_status; Type: TYPE; Schema: public; Owner: rasterfoundry
--

CREATE TYPE public.export_status AS ENUM (
    'NOTEXPORTED',
    'TOBEEXPORTED',
    'EXPORTING',
    'EXPORTED',
    'FAILED'
);


ALTER TYPE public.export_status OWNER TO rasterfoundry;

--
-- Name: export_type; Type: TYPE; Schema: public; Owner: rasterfoundry
--

CREATE TYPE public.export_type AS ENUM (
    'DROPBOX',
    'S3',
    'LOCAL'
);


ALTER TYPE public.export_type OWNER TO rasterfoundry;

--
-- Name: file_type; Type: TYPE; Schema: public; Owner: rasterfoundry
--

CREATE TYPE public.file_type AS ENUM (
    'GEOTIFF',
    'GEOTIFF_WITH_METADATA'
);


ALTER TYPE public.file_type OWNER TO rasterfoundry;

--
-- Name: group_role; Type: TYPE; Schema: public; Owner: rasterfoundry
--

CREATE TYPE public.group_role AS ENUM (
    'ADMIN',
    'MEMBER'
);


ALTER TYPE public.group_role OWNER TO rasterfoundry;

--
-- Name: group_type; Type: TYPE; Schema: public; Owner: rasterfoundry
--

CREATE TYPE public.group_type AS ENUM (
    'PLATFORM',
    'ORGANIZATION',
    'TEAM'
);


ALTER TYPE public.group_type OWNER TO rasterfoundry;

--
-- Name: ingest_status; Type: TYPE; Schema: public; Owner: rasterfoundry
--

CREATE TYPE public.ingest_status AS ENUM (
    'NOTINGESTED',
    'QUEUED',
    'TOBEINGESTED',
    'INGESTING',
    'INGESTED',
    'FAILED'
);


ALTER TYPE public.ingest_status OWNER TO rasterfoundry;

--
-- Name: job_status; Type: TYPE; Schema: public; Owner: rasterfoundry
--

CREATE TYPE public.job_status AS ENUM (
    'UPLOADING',
    'SUCCESS',
    'FAILURE',
    'PARTIALFAILURE',
    'QUEUED',
    'PROCESSING'
);


ALTER TYPE public.job_status OWNER TO rasterfoundry;

--
-- Name: membership_status; Type: TYPE; Schema: public; Owner: rasterfoundry
--

CREATE TYPE public.membership_status AS ENUM (
    'REQUESTED',
    'INVITED',
    'APPROVED'
);


ALTER TYPE public.membership_status OWNER TO rasterfoundry;

--
-- Name: object_type; Type: TYPE; Schema: public; Owner: rasterfoundry
--

CREATE TYPE public.object_type AS ENUM (
    'PROJECT',
    'SCENE',
    'DATASOURCE',
    'SHAPE',
    'WORKSPACE',
    'TEMPLATE',
    'ANALYSIS'
);


ALTER TYPE public.object_type OWNER TO rasterfoundry;

--
-- Name: org_status; Type: TYPE; Schema: public; Owner: rasterfoundry
--

CREATE TYPE public.org_status AS ENUM (
    'INACTIVE',
    'REQUESTED',
    'ACTIVE'
);


ALTER TYPE public.org_status OWNER TO rasterfoundry;

--
-- Name: organization_type; Type: TYPE; Schema: public; Owner: rasterfoundry
--

CREATE TYPE public.organization_type AS ENUM (
    'COMMERCIAL',
    'GOVERNMENT',
    'NON-PROFIT',
    'ACADEMIC',
    'MILITARY',
    'OTHER'
);


ALTER TYPE public.organization_type OWNER TO rasterfoundry;

--
-- Name: scene_type; Type: TYPE; Schema: public; Owner: rasterfoundry
--

CREATE TYPE public.scene_type AS ENUM (
    'AVRO',
    'COG'
);


ALTER TYPE public.scene_type OWNER TO rasterfoundry;

--
-- Name: subject_type; Type: TYPE; Schema: public; Owner: rasterfoundry
--

CREATE TYPE public.subject_type AS ENUM (
    'ALL',
    'PLATFORM',
    'ORGANIZATION',
    'TEAM',
    'USER'
);


ALTER TYPE public.subject_type OWNER TO rasterfoundry;

--
-- Name: thumbnailsize; Type: TYPE; Schema: public; Owner: rasterfoundry
--

CREATE TYPE public.thumbnailsize AS ENUM (
    'SMALL',
    'LARGE',
    'SQUARE'
);


ALTER TYPE public.thumbnailsize OWNER TO rasterfoundry;

--
-- Name: upload_status; Type: TYPE; Schema: public; Owner: rasterfoundry
--

CREATE TYPE public.upload_status AS ENUM (
    'CREATED',
    'UPLOADING',
    'UPLOADED',
    'QUEUED',
    'PROCESSING',
    'COMPLETE',
    'FAILED',
    'ABORTED'
);


ALTER TYPE public.upload_status OWNER TO rasterfoundry;

--
-- Name: upload_type; Type: TYPE; Schema: public; Owner: rasterfoundry
--

CREATE TYPE public.upload_type AS ENUM (
    'DROPBOX',
    'S3',
    'LOCAL',
    'PLANET',
    'MODIS_USGS',
    'LANDSAT_HISTORICAL'
);


ALTER TYPE public.upload_type OWNER TO rasterfoundry;

--
-- Name: user_visibility; Type: TYPE; Schema: public; Owner: rasterfoundry
--

CREATE TYPE public.user_visibility AS ENUM (
    'PUBLIC',
    'PRIVATE'
);


ALTER TYPE public.user_visibility OWNER TO rasterfoundry;

--
-- Name: visibility; Type: TYPE; Schema: public; Owner: rasterfoundry
--

CREATE TYPE public.visibility AS ENUM (
    'PUBLIC',
    'ORGANIZATION',
    'PRIVATE'
);


ALTER TYPE public.visibility OWNER TO rasterfoundry;

SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: __migrations__; Type: TABLE; Schema: public; Owner: rasterfoundry
--

CREATE TABLE public.__migrations__ (
    id integer NOT NULL
);


ALTER TABLE public.__migrations__ OWNER TO rasterfoundry;

--
-- Name: access_control_rules; Type: TABLE; Schema: public; Owner: rasterfoundry
--

CREATE TABLE public.access_control_rules (
    id uuid NOT NULL,
    created_at timestamp without time zone NOT NULL,
    created_by character varying(255) NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    object_type public.object_type NOT NULL,
    object_id uuid NOT NULL,
    subject_type public.subject_type NOT NULL,
    subject_id text,
    action_type public.action_type NOT NULL
);


ALTER TABLE public.access_control_rules OWNER TO rasterfoundry;

--
-- Name: annotation_groups; Type: TABLE; Schema: public; Owner: rasterfoundry
--

CREATE TABLE public.annotation_groups (
    id uuid NOT NULL,
    name text NOT NULL,
    created_at timestamp without time zone NOT NULL,
    created_by character varying(255) NOT NULL,
    modified_at timestamp without time zone NOT NULL,
    modified_by character varying(255) NOT NULL,
    project_id uuid NOT NULL,
    default_style jsonb,
    project_layer_id uuid NOT NULL
);


ALTER TABLE public.annotation_groups OWNER TO rasterfoundry;

--
-- Name: annotations; Type: TABLE; Schema: public; Owner: rasterfoundry
--

CREATE TABLE public.annotations (
    id uuid NOT NULL,
    project_id uuid NOT NULL,
    created_at timestamp without time zone NOT NULL,
    created_by character varying(255) NOT NULL,
    modified_at timestamp without time zone NOT NULL,
    modified_by character varying(255) NOT NULL,
    owner character varying(255) NOT NULL,
    label text NOT NULL,
    description text,
    machine_generated boolean DEFAULT false,
    confidence real,
    quality public.annotation_quality,
    geometry public.geometry(Geometry,3857),
    annotation_group uuid NOT NULL,
    labeled_by character varying(255),
    verified_by character varying(255),
    project_layer_id uuid NOT NULL
);


ALTER TABLE public.annotations OWNER TO rasterfoundry;

--
-- Name: aois; Type: TABLE; Schema: public; Owner: rasterfoundry
--

CREATE TABLE public.aois (
    id uuid NOT NULL,
    created_at timestamp without time zone NOT NULL,
    modified_at timestamp without time zone NOT NULL,
    created_by character varying(255) NOT NULL,
    modified_by character varying(255) NOT NULL,
    filters jsonb NOT NULL,
    owner character varying(255) NOT NULL,
    is_active boolean DEFAULT false NOT NULL,
    approval_required boolean DEFAULT false,
    start_time timestamp without time zone NOT NULL,
    project_id uuid NOT NULL,
    shape uuid NOT NULL
);


ALTER TABLE public.aois OWNER TO rasterfoundry;

--
-- Name: bands; Type: TABLE; Schema: public; Owner: rasterfoundry
--

CREATE TABLE public.bands (
    id uuid NOT NULL,
    image_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    number integer NOT NULL,
    wavelength integer[] NOT NULL
);


ALTER TABLE public.bands OWNER TO rasterfoundry;

--
-- Name: datasources; Type: TABLE; Schema: public; Owner: rasterfoundry
--

CREATE TABLE public.datasources (
    id uuid NOT NULL,
    created_at timestamp without time zone NOT NULL,
    created_by character varying(255) NOT NULL,
    modified_at timestamp without time zone NOT NULL,
    modified_by character varying(255) NOT NULL,
    name text NOT NULL,
    visibility public.visibility NOT NULL,
    extras jsonb DEFAULT '{}'::jsonb NOT NULL,
    composites jsonb DEFAULT '{}'::jsonb NOT NULL,
    owner character varying(255) NOT NULL,
    bands jsonb DEFAULT '{}'::jsonb NOT NULL,
    license_name character varying(255),
    acrs text[] DEFAULT '{}'::text[] NOT NULL
);


ALTER TABLE public.datasources OWNER TO rasterfoundry;

--
-- Name: exports; Type: TABLE; Schema: public; Owner: rasterfoundry
--

CREATE TABLE public.exports (
    id uuid NOT NULL,
    created_at timestamp without time zone NOT NULL,
    created_by character varying(255) NOT NULL,
    modified_at timestamp without time zone NOT NULL,
    modified_by character varying(255) NOT NULL,
    project_id uuid,
    visibility public.visibility NOT NULL,
    export_status public.export_status NOT NULL,
    export_type public.export_type NOT NULL,
    export_options jsonb NOT NULL,
    owner character varying(255) NOT NULL,
    toolrun_id uuid,
    project_layer_id uuid
);


ALTER TABLE public.exports OWNER TO rasterfoundry;

--
-- Name: feature_flags; Type: TABLE; Schema: public; Owner: rasterfoundry
--

CREATE TABLE public.feature_flags (
    id uuid NOT NULL,
    key character varying(255) NOT NULL,
    active boolean DEFAULT false NOT NULL,
    name character varying(255) NOT NULL,
    description character varying(255) NOT NULL
);


ALTER TABLE public.feature_flags OWNER TO rasterfoundry;

--
-- Name: images; Type: TABLE; Schema: public; Owner: rasterfoundry
--

CREATE TABLE public.images (
    id uuid NOT NULL,
    created_at timestamp without time zone NOT NULL,
    modified_at timestamp without time zone NOT NULL,
    created_by character varying(255) NOT NULL,
    modified_by character varying(255) NOT NULL,
    raw_data_bytes bigint NOT NULL,
    visibility public.visibility NOT NULL,
    filename text NOT NULL,
    sourceuri text NOT NULL,
    scene uuid NOT NULL,
    image_metadata jsonb NOT NULL,
    resolution_meters real NOT NULL,
    metadata_files text[] DEFAULT '{}'::text[] NOT NULL,
    owner character varying(255) NOT NULL
);


ALTER TABLE public.images OWNER TO rasterfoundry;

--
-- Name: layer_attributes; Type: TABLE; Schema: public; Owner: rasterfoundry
--

CREATE TABLE public.layer_attributes (
    layer_name character varying(255) NOT NULL,
    zoom integer NOT NULL,
    name character varying(255) NOT NULL,
    value jsonb NOT NULL
);


ALTER TABLE public.layer_attributes OWNER TO rasterfoundry;

--
-- Name: licenses; Type: TABLE; Schema: public; Owner: rasterfoundry
--

CREATE TABLE public.licenses (
    short_name character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    url text NOT NULL,
    osi_approved boolean NOT NULL,
    id integer NOT NULL
);


ALTER TABLE public.licenses OWNER TO rasterfoundry;

--
-- Name: licenses_id_seq; Type: SEQUENCE; Schema: public; Owner: rasterfoundry
--

CREATE SEQUENCE public.licenses_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.licenses_id_seq OWNER TO rasterfoundry;

--
-- Name: licenses_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: rasterfoundry
--

ALTER SEQUENCE public.licenses_id_seq OWNED BY public.licenses.id;


--
-- Name: map_tokens; Type: TABLE; Schema: public; Owner: rasterfoundry
--

CREATE TABLE public.map_tokens (
    id uuid NOT NULL,
    created_at timestamp without time zone NOT NULL,
    created_by character varying(255) NOT NULL,
    modified_at timestamp without time zone NOT NULL,
    modified_by character varying(255) NOT NULL,
    project_id uuid,
    name text NOT NULL,
    owner character varying(255) NOT NULL,
    toolrun_id uuid
);


ALTER TABLE public.map_tokens OWNER TO rasterfoundry;

--
-- Name: metrics; Type: TABLE; Schema: public; Owner: rasterfoundry
--

CREATE TABLE public.metrics (
    period tsrange NOT NULL,
    metric_event jsonb NOT NULL,
    metric_value integer NOT NULL,
    requester character varying(255) NOT NULL
);


ALTER TABLE public.metrics OWNER TO rasterfoundry;

--
-- Name: organization_features; Type: TABLE; Schema: public; Owner: rasterfoundry
--

CREATE TABLE public.organization_features (
    organization uuid NOT NULL,
    feature_flag uuid NOT NULL,
    active boolean DEFAULT false NOT NULL
);


ALTER TABLE public.organization_features OWNER TO rasterfoundry;

--
-- Name: organizations; Type: TABLE; Schema: public; Owner: rasterfoundry
--

CREATE TABLE public.organizations (
    id uuid NOT NULL,
    created_at timestamp without time zone NOT NULL,
    modified_at timestamp without time zone NOT NULL,
    name character varying(255) NOT NULL,
    platform_id uuid DEFAULT '31277626-968b-4e40-840b-559d9c67863c'::uuid NOT NULL,
    dropbox_credential text DEFAULT ''::text NOT NULL,
    planet_credential text DEFAULT ''::text NOT NULL,
    logo_uri text DEFAULT ''::text NOT NULL,
    visibility public.visibility DEFAULT 'PRIVATE'::public.visibility NOT NULL,
    status public.org_status NOT NULL
);


ALTER TABLE public.organizations OWNER TO rasterfoundry;

--
-- Name: platforms; Type: TABLE; Schema: public; Owner: rasterfoundry
--

CREATE TABLE public.platforms (
    id uuid NOT NULL,
    name text NOT NULL,
    public_settings jsonb DEFAULT '{"emailFrom": "noreply@example.com", "emailSupport": "support@example.com", "platformHost": null, "emailSmtpHost": "", "emailSmtpPort": 465, "emailSmtpUserName": "", "emailSmtpEncryption": "ssl", "emailAoiNotification": false, "emailFromDisplayName": "", "emailExportNotification": false, "emailIngestNotification": false}'::jsonb NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    default_organization_id uuid,
    private_settings jsonb DEFAULT '{"emailPassword": ""}'::jsonb NOT NULL
);


ALTER TABLE public.platforms OWNER TO rasterfoundry;

--
-- Name: project_layers; Type: TABLE; Schema: public; Owner: rasterfoundry
--

CREATE TABLE public.project_layers (
    id uuid NOT NULL,
    created_at timestamp without time zone NOT NULL,
    modified_at timestamp without time zone NOT NULL,
    name text NOT NULL,
    project_id uuid,
    color_group_hex text NOT NULL,
    range_start timestamp without time zone,
    range_end timestamp without time zone,
    geometry public.geometry(Geometry,3857),
    smart_layer_id uuid,
    is_single_band boolean DEFAULT false NOT NULL,
    single_band_options jsonb,
    overviews_location text,
    min_zoom_level integer
);


ALTER TABLE public.project_layers OWNER TO rasterfoundry;

--
-- Name: projects; Type: TABLE; Schema: public; Owner: rasterfoundry
--

CREATE TABLE public.projects (
    id uuid NOT NULL,
    created_at timestamp without time zone NOT NULL,
    modified_at timestamp without time zone NOT NULL,
    created_by character varying(255) NOT NULL,
    modified_by character varying(255) NOT NULL,
    name text NOT NULL,
    slug_label character varying(255) NOT NULL,
    description text NOT NULL,
    visibility public.visibility NOT NULL,
    tags text[] NOT NULL,
    manual_order boolean DEFAULT true NOT NULL,
    extent public.geometry(Polygon,3857),
    tile_visibility public.visibility DEFAULT 'PRIVATE'::public.visibility NOT NULL,
    is_aoi_project boolean DEFAULT false NOT NULL,
    aoi_cadence_millis bigint DEFAULT 604800000 NOT NULL,
    aois_last_checked timestamp without time zone DEFAULT now() NOT NULL,
    owner character varying(255) NOT NULL,
    is_single_band boolean DEFAULT false NOT NULL,
    single_band_options jsonb,
    default_annotation_group uuid,
    extras jsonb DEFAULT '{}'::jsonb,
    acrs text[] DEFAULT '{}'::text[] NOT NULL,
    default_layer_id uuid NOT NULL
);


ALTER TABLE public.projects OWNER TO rasterfoundry;

--
-- Name: scenes; Type: TABLE; Schema: public; Owner: rasterfoundry
--

CREATE TABLE public.scenes (
    id uuid NOT NULL,
    created_at timestamp without time zone NOT NULL,
    modified_at timestamp without time zone NOT NULL,
    created_by character varying(255) NOT NULL,
    modified_by character varying(255) NOT NULL,
    visibility public.visibility NOT NULL,
    tags text[] NOT NULL,
    scene_metadata jsonb NOT NULL,
    cloud_cover real,
    acquisition_date timestamp without time zone,
    thumbnail_status public.job_status NOT NULL,
    boundary_status public.job_status NOT NULL,
    sun_azimuth real,
    sun_elevation real,
    name character varying(255) NOT NULL,
    data_footprint public.geometry(MultiPolygon,3857),
    metadata_files text[] DEFAULT '{}'::text[] NOT NULL,
    tile_footprint public.geometry(MultiPolygon,3857),
    ingest_location text,
    datasource uuid NOT NULL,
    ingest_status public.ingest_status NOT NULL,
    owner character varying(255) NOT NULL,
    scene_type public.scene_type DEFAULT 'AVRO'::public.scene_type,
    acrs text[] DEFAULT '{}'::text[] NOT NULL
);


ALTER TABLE public.scenes OWNER TO rasterfoundry;

--
-- Name: scenes_to_layers; Type: TABLE; Schema: public; Owner: rasterfoundry
--

CREATE TABLE public.scenes_to_layers (
    scene_id uuid NOT NULL,
    project_layer_id uuid NOT NULL,
    scene_order integer,
    mosaic_definition jsonb DEFAULT '{}'::json NOT NULL,
    accepted boolean DEFAULT true NOT NULL
);


ALTER TABLE public.scenes_to_layers OWNER TO rasterfoundry;

--
-- Name: shapes; Type: TABLE; Schema: public; Owner: rasterfoundry
--

CREATE TABLE public.shapes (
    id uuid NOT NULL,
    created_at timestamp without time zone NOT NULL,
    created_by character varying(255) NOT NULL,
    modified_at timestamp without time zone NOT NULL,
    modified_by character varying(255) NOT NULL,
    owner character varying(255) NOT NULL,
    name text NOT NULL,
    description text,
    geometry public.geometry(Geometry,3857) NOT NULL,
    acrs text[] DEFAULT '{}'::text[] NOT NULL
);


ALTER TABLE public.shapes OWNER TO rasterfoundry;

--
-- Name: teams; Type: TABLE; Schema: public; Owner: rasterfoundry
--

CREATE TABLE public.teams (
    id uuid NOT NULL,
    created_at timestamp without time zone NOT NULL,
    created_by character varying(255) NOT NULL,
    modified_at timestamp without time zone NOT NULL,
    modified_by character varying(255) NOT NULL,
    organization_id uuid NOT NULL,
    name text NOT NULL,
    settings jsonb DEFAULT '{}'::jsonb NOT NULL,
    is_active boolean DEFAULT true NOT NULL
);


ALTER TABLE public.teams OWNER TO rasterfoundry;

--
-- Name: thumbnails; Type: TABLE; Schema: public; Owner: rasterfoundry
--

CREATE TABLE public.thumbnails (
    id uuid NOT NULL,
    created_at timestamp without time zone NOT NULL,
    modified_at timestamp without time zone NOT NULL,
    width_px integer NOT NULL,
    height_px integer NOT NULL,
    scene uuid NOT NULL,
    url character varying(255) NOT NULL,
    thumbnail_size public.thumbnailsize NOT NULL
);


ALTER TABLE public.thumbnails OWNER TO rasterfoundry;

--
-- Name: tool_runs; Type: TABLE; Schema: public; Owner: rasterfoundry
--

CREATE TABLE public.tool_runs (
    id uuid NOT NULL,
    created_at timestamp without time zone NOT NULL,
    created_by character varying(255) NOT NULL,
    modified_at timestamp without time zone NOT NULL,
    modified_by character varying(255) NOT NULL,
    visibility public.visibility NOT NULL,
    execution_parameters jsonb DEFAULT '{}'::jsonb NOT NULL,
    owner character varying(255) NOT NULL,
    name text DEFAULT ''::text,
    acrs text[] DEFAULT '{}'::text[] NOT NULL,
    project_id uuid,
    project_layer_id uuid,
    template_id uuid
);


ALTER TABLE public.tool_runs OWNER TO rasterfoundry;

--
-- Name: tools; Type: TABLE; Schema: public; Owner: rasterfoundry
--

CREATE TABLE public.tools (
    id uuid NOT NULL,
    created_at timestamp without time zone NOT NULL,
    modified_at timestamp without time zone NOT NULL,
    created_by character varying(255) NOT NULL,
    modified_by character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    description text NOT NULL,
    requirements text NOT NULL,
    visibility public.visibility NOT NULL,
    compatible_data_sources text[] DEFAULT '{}'::text[] NOT NULL,
    stars real DEFAULT 0.0 NOT NULL,
    definition jsonb DEFAULT '{}'::jsonb NOT NULL,
    owner character varying(255) NOT NULL,
    acrs text[] DEFAULT '{}'::text[] NOT NULL,
    license integer,
    single_source boolean DEFAULT false NOT NULL
);


ALTER TABLE public.tools OWNER TO rasterfoundry;

--
-- Name: uploads; Type: TABLE; Schema: public; Owner: rasterfoundry
--

CREATE TABLE public.uploads (
    id uuid NOT NULL,
    created_at timestamp without time zone NOT NULL,
    created_by character varying(255) NOT NULL,
    modified_at timestamp without time zone NOT NULL,
    modified_by character varying(255) NOT NULL,
    upload_status public.upload_status NOT NULL,
    file_type public.file_type NOT NULL,
    upload_type public.upload_type NOT NULL,
    files text[] DEFAULT '{}'::text[] NOT NULL,
    datasource uuid NOT NULL,
    metadata jsonb NOT NULL,
    visibility public.visibility DEFAULT 'PRIVATE'::public.visibility NOT NULL,
    owner character varying(255) NOT NULL,
    project_id uuid,
    source text,
    layer_id uuid
);


ALTER TABLE public.uploads OWNER TO rasterfoundry;

--
-- Name: user_group_roles; Type: TABLE; Schema: public; Owner: rasterfoundry
--

CREATE TABLE public.user_group_roles (
    id uuid NOT NULL,
    created_at timestamp without time zone NOT NULL,
    created_by character varying(255) NOT NULL,
    modified_at timestamp without time zone NOT NULL,
    modified_by character varying(255) NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    user_id character varying(255) NOT NULL,
    group_type public.group_type NOT NULL,
    group_id uuid NOT NULL,
    group_role public.group_role NOT NULL,
    membership_status public.membership_status DEFAULT 'APPROVED'::public.membership_status NOT NULL
);


ALTER TABLE public.user_group_roles OWNER TO rasterfoundry;

--
-- Name: users; Type: TABLE; Schema: public; Owner: rasterfoundry
--

CREATE TABLE public.users (
    id character varying(255) NOT NULL,
    role character varying(255) DEFAULT 'VIEWER'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT timezone('utc'::text, now()) NOT NULL,
    modified_at timestamp without time zone DEFAULT timezone('utc'::text, now()) NOT NULL,
    dropbox_credential text DEFAULT ''::text NOT NULL,
    planet_credential text DEFAULT ''::text NOT NULL,
    email_notifications boolean DEFAULT false NOT NULL,
    email character varying(255) DEFAULT ''::character varying NOT NULL,
    name character varying(255) DEFAULT ''::character varying NOT NULL,
    profile_image_uri text DEFAULT ''::text NOT NULL,
    is_superuser boolean DEFAULT false NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    visibility public.user_visibility DEFAULT 'PRIVATE'::public.user_visibility,
    personal_info jsonb DEFAULT '{"email": "", "lastName": "", "firstName": "", "profileBio": "", "profileUrl": "", "phoneNumber": "", "profileWebsite": "", "organizationName": "", "organizationType": "OTHER", "emailNotifications": false, "organizationWebsite": ""}'::jsonb NOT NULL
);


ALTER TABLE public.users OWNER TO rasterfoundry;

--
-- Name: licenses id; Type: DEFAULT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.licenses ALTER COLUMN id SET DEFAULT nextval('public.licenses_id_seq'::regclass);


--
-- Name: __migrations__ __migrations___pkey; Type: CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.__migrations__
    ADD CONSTRAINT __migrations___pkey PRIMARY KEY (id);


--
-- Name: access_control_rules access_control_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.access_control_rules
    ADD CONSTRAINT access_control_rules_pkey PRIMARY KEY (id);


--
-- Name: annotation_groups annotation_groups_pkey; Type: CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.annotation_groups
    ADD CONSTRAINT annotation_groups_pkey PRIMARY KEY (id);


--
-- Name: annotations annotations_pkey; Type: CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.annotations
    ADD CONSTRAINT annotations_pkey PRIMARY KEY (id);


--
-- Name: aois aois_pkey; Type: CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.aois
    ADD CONSTRAINT aois_pkey PRIMARY KEY (id);


--
-- Name: bands bands_pkey; Type: CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.bands
    ADD CONSTRAINT bands_pkey PRIMARY KEY (id);


--
-- Name: datasources datasources_pkey; Type: CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.datasources
    ADD CONSTRAINT datasources_pkey PRIMARY KEY (id);


--
-- Name: exports exports_pkey; Type: CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.exports
    ADD CONSTRAINT exports_pkey PRIMARY KEY (id);


--
-- Name: feature_flags feature_flags_key_key; Type: CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.feature_flags
    ADD CONSTRAINT feature_flags_key_key UNIQUE (key);


--
-- Name: feature_flags feature_flags_pkey; Type: CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.feature_flags
    ADD CONSTRAINT feature_flags_pkey PRIMARY KEY (id);


--
-- Name: licenses id_is_unique; Type: CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.licenses
    ADD CONSTRAINT id_is_unique UNIQUE (id);


--
-- Name: images images_pkey; Type: CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.images
    ADD CONSTRAINT images_pkey PRIMARY KEY (id);


--
-- Name: layer_attributes layer_attributes_pkey; Type: CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.layer_attributes
    ADD CONSTRAINT layer_attributes_pkey PRIMARY KEY (layer_name, zoom, name);


--
-- Name: licenses licenses_pkey; Type: CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.licenses
    ADD CONSTRAINT licenses_pkey PRIMARY KEY (short_name);


--
-- Name: map_tokens map_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.map_tokens
    ADD CONSTRAINT map_tokens_pkey PRIMARY KEY (id);


--
-- Name: metrics metrics_pkey; Type: CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.metrics ADD CONSTRAINT metric_event_period_unique
  UNIQUE (period, metric_event, requester);

--
-- Name: organization_features organization_features_pkey; Type: CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.organization_features
    ADD CONSTRAINT organization_features_pkey PRIMARY KEY (organization, feature_flag);


--
-- Name: organizations organizations_pkey; Type: CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.organizations
    ADD CONSTRAINT organizations_pkey PRIMARY KEY (id);


--
-- Name: platforms platforms_pkey; Type: CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.platforms
    ADD CONSTRAINT platforms_pkey PRIMARY KEY (id);


--
-- Name: project_layers project_layers_pkey; Type: CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.project_layers
    ADD CONSTRAINT project_layers_pkey PRIMARY KEY (id);


--
-- Name: projects projects_pkey; Type: CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT projects_pkey PRIMARY KEY (id);


--
-- Name: scenes scenes_pkey; Type: CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.scenes
    ADD CONSTRAINT scenes_pkey PRIMARY KEY (id);


--
-- Name: scenes_to_layers scenes_to_layers_pkey; Type: CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.scenes_to_layers
    ADD CONSTRAINT scenes_to_layers_pkey PRIMARY KEY (scene_id, project_layer_id);


--
-- Name: shapes shapes_pkey; Type: CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.shapes
    ADD CONSTRAINT shapes_pkey PRIMARY KEY (id);


--
-- Name: teams teams_pkey; Type: CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.teams
    ADD CONSTRAINT teams_pkey PRIMARY KEY (id);


--
-- Name: thumbnails thumbnails_pkey; Type: CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.thumbnails
    ADD CONSTRAINT thumbnails_pkey PRIMARY KEY (id);


--
-- Name: tool_runs tool_runs_pkey; Type: CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.tool_runs
    ADD CONSTRAINT tool_runs_pkey PRIMARY KEY (id);


--
-- Name: tools tools_pkey; Type: CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.tools
    ADD CONSTRAINT tools_pkey PRIMARY KEY (id);


--
-- Name: uploads uploads_pkey; Type: CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.uploads
    ADD CONSTRAINT uploads_pkey PRIMARY KEY (id);


--
-- Name: user_group_roles user_group_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.user_group_roles
    ADD CONSTRAINT user_group_roles_pkey PRIMARY KEY (id);


--
-- Name: users users_auth_id_key; Type: CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_auth_id_key UNIQUE (id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: acquisition_date_idx; Type: INDEX; Schema: public; Owner: rasterfoundry
--

CREATE INDEX acquisition_date_idx ON public.scenes USING btree (acquisition_date);


--
-- Name: act_typ_idx; Type: INDEX; Schema: public; Owner: rasterfoundry
--

CREATE INDEX act_typ_idx ON public.access_control_rules USING btree (action_type);


--
-- Name: created_at_idx; Type: INDEX; Schema: public; Owner: rasterfoundry
--

CREATE INDEX created_at_idx ON public.scenes USING btree (created_at);


--
-- Name: data_footprint_idx; Type: INDEX; Schema: public; Owner: rasterfoundry
--

CREATE INDEX data_footprint_idx ON public.scenes USING gist (data_footprint);


--
-- Name: datasource_idx; Type: INDEX; Schema: public; Owner: rasterfoundry
--

CREATE INDEX datasource_idx ON public.scenes USING btree (datasource);


--
-- Name: datasources_acrs; Type: INDEX; Schema: public; Owner: rasterfoundry
--

CREATE INDEX datasources_acrs ON public.datasources USING gin (acrs);


--
-- Name: map_token_project_idx; Type: INDEX; Schema: public; Owner: rasterfoundry
--

CREATE UNIQUE INDEX map_token_project_idx ON public.map_tokens USING btree (project_id);


--
-- Name: map_token_toolrun_idx; Type: INDEX; Schema: public; Owner: rasterfoundry
--

CREATE UNIQUE INDEX map_token_toolrun_idx ON public.map_tokens USING btree (toolrun_id);


--
-- Name: metrics_metric_event_idx; Type: INDEX; Schema: public; Owner: rasterfoundry
--

CREATE INDEX metrics_metric_event_idx ON public.metrics USING gin (metric_event);


--
-- Name: metrics_period_idx; Type: INDEX; Schema: public; Owner: rasterfoundry
--

CREATE INDEX metrics_period_idx ON public.metrics USING btree (period);


--
-- Name: name_idx; Type: INDEX; Schema: public; Owner: rasterfoundry
--

CREATE INDEX name_idx ON public.scenes USING btree (name);


--
-- Name: obj_act_idx; Type: INDEX; Schema: public; Owner: rasterfoundry
--

CREATE INDEX obj_act_idx ON public.access_control_rules USING btree (object_type, action_type);


--
-- Name: obj_id_idx; Type: INDEX; Schema: public; Owner: rasterfoundry
--

CREATE INDEX obj_id_idx ON public.access_control_rules USING btree (object_id);


--
-- Name: obj_typ_idx; Type: INDEX; Schema: public; Owner: rasterfoundry
--

CREATE INDEX obj_typ_idx ON public.access_control_rules USING btree (object_type);


--
-- Name: projects_acrs; Type: INDEX; Schema: public; Owner: rasterfoundry
--

CREATE INDEX projects_acrs ON public.projects USING gin (acrs);


--
-- Name: scenes_acrs; Type: INDEX; Schema: public; Owner: rasterfoundry
--

CREATE INDEX scenes_acrs ON public.scenes USING gin (acrs);


--
-- Name: scenes_date_id_idx; Type: INDEX; Schema: public; Owner: rasterfoundry
--

CREATE INDEX scenes_date_id_idx ON public.scenes USING btree ((COALESCE(acquisition_date, created_at)), id);


--
-- Name: scenes_sort_date; Type: INDEX; Schema: public; Owner: rasterfoundry
--

CREATE INDEX scenes_sort_date ON public.scenes USING btree ((COALESCE(acquisition_date, created_at)));


--
-- Name: shapes_acrs; Type: INDEX; Schema: public; Owner: rasterfoundry
--

CREATE INDEX shapes_acrs ON public.shapes USING gin (acrs);


--
-- Name: sub_id_idx; Type: INDEX; Schema: public; Owner: rasterfoundry
--

CREATE INDEX sub_id_idx ON public.access_control_rules USING btree (subject_id);


--
-- Name: sub_typ_idx; Type: INDEX; Schema: public; Owner: rasterfoundry
--

CREATE INDEX sub_typ_idx ON public.access_control_rules USING btree (subject_type);


--
-- Name: tile_footprint_idx; Type: INDEX; Schema: public; Owner: rasterfoundry
--

CREATE INDEX tile_footprint_idx ON public.scenes USING gist (tile_footprint);


--
-- Name: tool_runs_acrs; Type: INDEX; Schema: public; Owner: rasterfoundry
--

CREATE INDEX tool_runs_acrs ON public.tool_runs USING gin (acrs);


--
-- Name: tools_acrs; Type: INDEX; Schema: public; Owner: rasterfoundry
--

CREATE INDEX tools_acrs ON public.tools USING gin (acrs);


--
-- Name: user_group_role_unique_role; Type: INDEX; Schema: public; Owner: rasterfoundry
--

CREATE UNIQUE INDEX user_group_role_unique_role ON public.user_group_roles USING btree (group_id, user_id) WHERE (is_active = true);


--
-- Name: visibility_idx; Type: INDEX; Schema: public; Owner: rasterfoundry
--

CREATE INDEX visibility_idx ON public.scenes USING btree (visibility);


--
-- Name: access_control_rules access_control_rules_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.access_control_rules
    ADD CONSTRAINT access_control_rules_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: annotation_groups annotation_groups_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.annotation_groups
    ADD CONSTRAINT annotation_groups_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: annotation_groups annotation_groups_modified_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.annotation_groups
    ADD CONSTRAINT annotation_groups_modified_by_fkey FOREIGN KEY (modified_by) REFERENCES public.users(id);


--
-- Name: annotation_groups annotation_groups_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.annotation_groups
    ADD CONSTRAINT annotation_groups_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id) ON DELETE CASCADE;


--
-- Name: annotation_groups annotation_groups_project_layer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.annotation_groups
    ADD CONSTRAINT annotation_groups_project_layer_id_fkey FOREIGN KEY (project_layer_id) REFERENCES public.project_layers(id) ON DELETE CASCADE;


--
-- Name: annotations annotations_annotation_group_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.annotations
    ADD CONSTRAINT annotations_annotation_group_fkey FOREIGN KEY (annotation_group) REFERENCES public.annotation_groups(id) ON DELETE CASCADE;


--
-- Name: annotations annotations_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.annotations
    ADD CONSTRAINT annotations_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: annotations annotations_labeled_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.annotations
    ADD CONSTRAINT annotations_labeled_by_fkey FOREIGN KEY (labeled_by) REFERENCES public.users(id);


--
-- Name: annotations annotations_modified_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.annotations
    ADD CONSTRAINT annotations_modified_by_fkey FOREIGN KEY (modified_by) REFERENCES public.users(id);


--
-- Name: annotations annotations_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.annotations
    ADD CONSTRAINT annotations_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id) ON DELETE CASCADE;


--
-- Name: annotations annotations_project_layer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.annotations
    ADD CONSTRAINT annotations_project_layer_id_fkey FOREIGN KEY (project_layer_id) REFERENCES public.project_layers(id) ON DELETE CASCADE;


--
-- Name: annotations annotations_verified_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.annotations
    ADD CONSTRAINT annotations_verified_by_fkey FOREIGN KEY (verified_by) REFERENCES public.users(id);


--
-- Name: aois aoi_to_project_id; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.aois
    ADD CONSTRAINT aoi_to_project_id FOREIGN KEY (project_id) REFERENCES public.projects(id) ON DELETE CASCADE;


--
-- Name: aois aois_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.aois
    ADD CONSTRAINT aois_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: aois aois_modified_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.aois
    ADD CONSTRAINT aois_modified_by_fkey FOREIGN KEY (modified_by) REFERENCES public.users(id);


--
-- Name: aois aois_owner_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.aois
    ADD CONSTRAINT aois_owner_fkey FOREIGN KEY (owner) REFERENCES public.users(id) NOT VALID;


--
-- Name: aois aois_shape_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.aois
    ADD CONSTRAINT aois_shape_fkey FOREIGN KEY (shape) REFERENCES public.shapes(id);


--
-- Name: bands bands_image_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.bands
    ADD CONSTRAINT bands_image_id_fkey FOREIGN KEY (image_id) REFERENCES public.images(id) ON DELETE CASCADE;


--
-- Name: datasources datasources_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.datasources
    ADD CONSTRAINT datasources_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: datasources datasources_license_name_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.datasources
    ADD CONSTRAINT datasources_license_name_fkey FOREIGN KEY (license_name) REFERENCES public.licenses(short_name) ON DELETE SET NULL;


--
-- Name: datasources datasources_modified_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.datasources
    ADD CONSTRAINT datasources_modified_by_fkey FOREIGN KEY (modified_by) REFERENCES public.users(id);


--
-- Name: datasources datasources_owner_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.datasources
    ADD CONSTRAINT datasources_owner_fkey FOREIGN KEY (owner) REFERENCES public.users(id) NOT VALID;


--
-- Name: exports exports_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.exports
    ADD CONSTRAINT exports_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: exports exports_modified_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.exports
    ADD CONSTRAINT exports_modified_by_fkey FOREIGN KEY (modified_by) REFERENCES public.users(id);


--
-- Name: exports exports_owner_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.exports
    ADD CONSTRAINT exports_owner_fkey FOREIGN KEY (owner) REFERENCES public.users(id) NOT VALID;


--
-- Name: exports exports_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.exports
    ADD CONSTRAINT exports_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id) ON DELETE CASCADE;


--
-- Name: exports exports_project_layer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.exports
    ADD CONSTRAINT exports_project_layer_id_fkey FOREIGN KEY (project_layer_id) REFERENCES public.project_layers(id) ON DELETE CASCADE;


--
-- Name: exports exports_toolrun_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.exports
    ADD CONSTRAINT exports_toolrun_id_fkey FOREIGN KEY (toolrun_id) REFERENCES public.tool_runs(id) ON DELETE CASCADE;


--
-- Name: organization_features exports_toolrun_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.organization_features
    ADD CONSTRAINT exports_toolrun_id_fkey FOREIGN KEY (organization) REFERENCES public.organizations(id) ON DELETE CASCADE;


--
-- Name: images images_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.images
    ADD CONSTRAINT images_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: images images_modified_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.images
    ADD CONSTRAINT images_modified_by_fkey FOREIGN KEY (modified_by) REFERENCES public.users(id);


--
-- Name: images images_owner_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.images
    ADD CONSTRAINT images_owner_fkey FOREIGN KEY (owner) REFERENCES public.users(id) NOT VALID;


--
-- Name: images images_scene_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.images
    ADD CONSTRAINT images_scene_fkey FOREIGN KEY (scene) REFERENCES public.scenes(id) ON DELETE CASCADE;


--
-- Name: map_tokens map_tokens_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.map_tokens
    ADD CONSTRAINT map_tokens_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: map_tokens map_tokens_modified_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.map_tokens
    ADD CONSTRAINT map_tokens_modified_by_fkey FOREIGN KEY (modified_by) REFERENCES public.users(id);


--
-- Name: map_tokens map_tokens_owner_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.map_tokens
    ADD CONSTRAINT map_tokens_owner_fkey FOREIGN KEY (owner) REFERENCES public.users(id) NOT VALID;


--
-- Name: map_tokens map_tokens_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.map_tokens
    ADD CONSTRAINT map_tokens_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id) ON DELETE CASCADE;


--
-- Name: map_tokens map_tokens_toolrun_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.map_tokens
    ADD CONSTRAINT map_tokens_toolrun_id_fkey FOREIGN KEY (toolrun_id) REFERENCES public.tool_runs(id) ON DELETE CASCADE;


--
-- Name: organization_features organization_features_feature_flag_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.organization_features
    ADD CONSTRAINT organization_features_feature_flag_fkey FOREIGN KEY (feature_flag) REFERENCES public.feature_flags(id) ON DELETE CASCADE;


--
-- Name: organizations organizations_platform_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.organizations
    ADD CONSTRAINT organizations_platform_id_fkey FOREIGN KEY (platform_id) REFERENCES public.platforms(id) ON DELETE CASCADE;


--
-- Name: platforms platforms_default_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.platforms
    ADD CONSTRAINT platforms_default_organization_id_fkey FOREIGN KEY (default_organization_id) REFERENCES public.organizations(id);


--
-- Name: project_layers project_layers_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.project_layers
    ADD CONSTRAINT project_layers_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id) ON DELETE CASCADE;


--
-- Name: projects projects_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT projects_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: projects projects_default_annotation_group_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT projects_default_annotation_group_fkey FOREIGN KEY (default_annotation_group) REFERENCES public.annotation_groups(id);


--
-- Name: projects projects_default_project_layer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT projects_default_project_layer_id_fkey FOREIGN KEY (default_layer_id) REFERENCES public.project_layers(id);


--
-- Name: projects projects_modified_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT projects_modified_by_fkey FOREIGN KEY (modified_by) REFERENCES public.users(id);


--
-- Name: projects projects_owner_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT projects_owner_fkey FOREIGN KEY (owner) REFERENCES public.users(id) NOT VALID;


--
-- Name: scenes scenes_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.scenes
    ADD CONSTRAINT scenes_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: scenes scenes_datasource_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.scenes
    ADD CONSTRAINT scenes_datasource_id_fkey FOREIGN KEY (datasource) REFERENCES public.datasources(id);


--
-- Name: scenes scenes_modified_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.scenes
    ADD CONSTRAINT scenes_modified_by_fkey FOREIGN KEY (modified_by) REFERENCES public.users(id);


--
-- Name: scenes scenes_owner_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.scenes
    ADD CONSTRAINT scenes_owner_fkey FOREIGN KEY (owner) REFERENCES public.users(id) NOT VALID;


--
-- Name: scenes_to_layers scenes_to_layers_project_layer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.scenes_to_layers
    ADD CONSTRAINT scenes_to_layers_project_layer_id_fkey FOREIGN KEY (project_layer_id) REFERENCES public.project_layers(id) ON DELETE CASCADE;


--
-- Name: scenes_to_layers scenes_to_layers_scene_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.scenes_to_layers
    ADD CONSTRAINT scenes_to_layers_scene_id_fkey FOREIGN KEY (scene_id) REFERENCES public.scenes(id) ON DELETE CASCADE;


--
-- Name: shapes shapes_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.shapes
    ADD CONSTRAINT shapes_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: shapes shapes_modified_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.shapes
    ADD CONSTRAINT shapes_modified_by_fkey FOREIGN KEY (modified_by) REFERENCES public.users(id);


--
-- Name: teams teams_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.teams
    ADD CONSTRAINT teams_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: teams teams_modified_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.teams
    ADD CONSTRAINT teams_modified_by_fkey FOREIGN KEY (modified_by) REFERENCES public.users(id);


--
-- Name: teams teams_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.teams
    ADD CONSTRAINT teams_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE CASCADE;


--
-- Name: thumbnails thumbnails_scene_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.thumbnails
    ADD CONSTRAINT thumbnails_scene_fkey FOREIGN KEY (scene) REFERENCES public.scenes(id) ON DELETE CASCADE;


--
-- Name: tool_runs tool_runs_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.tool_runs
    ADD CONSTRAINT tool_runs_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: tool_runs tool_runs_modified_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.tool_runs
    ADD CONSTRAINT tool_runs_modified_by_fkey FOREIGN KEY (modified_by) REFERENCES public.users(id);


--
-- Name: tool_runs tool_runs_owner_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.tool_runs
    ADD CONSTRAINT tool_runs_owner_fkey FOREIGN KEY (owner) REFERENCES public.users(id) NOT VALID;


--
-- Name: tool_runs tool_runs_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.tool_runs
    ADD CONSTRAINT tool_runs_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id) ON DELETE CASCADE;


--
-- Name: tool_runs tool_runs_project_layer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.tool_runs
    ADD CONSTRAINT tool_runs_project_layer_id_fkey FOREIGN KEY (project_layer_id) REFERENCES public.project_layers(id) ON DELETE CASCADE;


--
-- Name: tool_runs tool_runs_template_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.tool_runs
    ADD CONSTRAINT tool_runs_template_id_fkey FOREIGN KEY (template_id) REFERENCES public.tools(id) ON DELETE SET NULL;


--
-- Name: tools tools_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.tools
    ADD CONSTRAINT tools_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: tools tools_license_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.tools
    ADD CONSTRAINT tools_license_fkey FOREIGN KEY (license) REFERENCES public.licenses(id);


--
-- Name: tools tools_modified_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.tools
    ADD CONSTRAINT tools_modified_by_fkey FOREIGN KEY (modified_by) REFERENCES public.users(id);


--
-- Name: tools tools_owner_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.tools
    ADD CONSTRAINT tools_owner_fkey FOREIGN KEY (owner) REFERENCES public.users(id) NOT VALID;


--
-- Name: uploads upload_project_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.uploads
    ADD CONSTRAINT upload_project_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id) ON DELETE SET NULL;


--
-- Name: uploads uploads_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.uploads
    ADD CONSTRAINT uploads_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: uploads uploads_datasource_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.uploads
    ADD CONSTRAINT uploads_datasource_fkey FOREIGN KEY (datasource) REFERENCES public.datasources(id);


--
-- Name: uploads uploads_layer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.uploads
    ADD CONSTRAINT uploads_layer_id_fkey FOREIGN KEY (layer_id) REFERENCES public.project_layers(id) ON DELETE SET NULL;


--
-- Name: uploads uploads_modified_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.uploads
    ADD CONSTRAINT uploads_modified_by_fkey FOREIGN KEY (modified_by) REFERENCES public.users(id);


--
-- Name: uploads uploads_owner_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.uploads
    ADD CONSTRAINT uploads_owner_fkey FOREIGN KEY (owner) REFERENCES public.users(id) NOT VALID;


--
-- Name: user_group_roles user_group_roles_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.user_group_roles
    ADD CONSTRAINT user_group_roles_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: user_group_roles user_group_roles_modified_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.user_group_roles
    ADD CONSTRAINT user_group_roles_modified_by_fkey FOREIGN KEY (modified_by) REFERENCES public.users(id);


--
-- Name: user_group_roles user_group_roles_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: rasterfoundry
--

ALTER TABLE ONLY public.user_group_roles
    ADD CONSTRAINT user_group_roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- PostgreSQL database dump complete
--

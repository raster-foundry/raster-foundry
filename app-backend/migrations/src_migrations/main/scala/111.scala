import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M111 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(111)(
    List(
      sqlu"""
    CREATE TABLE platforms (
        id UUID PRIMARY KEY NOT NULL,
        name TEXT NOT NULL,
        settings JSONB NOT NULL default '{}'
    );

    INSERT INTO platforms (id, name)
    VALUES (
        '31277626-968b-4e40-840b-559d9c67863c',
        'Raster Foundry'
    );

    ALTER TABLE organizations ADD COLUMN platform_id UUID references platforms(id) NOT NULL default '31277626-968b-4e40-840b-559d9c67863c';

    CREATE TABLE teams (
        id UUID PRIMARY KEY NOT NULL,
        created_at TIMESTAMP NOT NULL,
        created_by VARCHAR(255) REFERENCES users(id) NOT NULL,
        modified_at TIMESTAMP NOT NULL,
        modified_by VARCHAR(255) REFERENCES users(id) NOT NULL,
        organization_id UUID REFERENCES organizations(id) NOT NULL,
        name text NOT NULL,
        settings JSONB NOT NULL default '{}'
    );

    CREATE TYPE group_type AS ENUM ('PLATFORM', 'ORGANIZATION', 'TEAM');
    CREATE TYPE group_role AS ENUM ('ADMIN', 'MEMBER');

    CREATE TABLE user_group_roles (
        id UUID PRIMARY KEY NOT NULL,
        created_at TIMESTAMP NOT NULL,
        created_by VARCHAR(255) REFERENCES users(id) NOT NULL,
        modified_at TIMESTAMP NOT NULL,
        modified_by VARCHAR(255) REFERENCES users(id) NOT NULL,
        is_active BOOLEAN DEFAULT true NOT NULL,
        user_id VARCHAR(255) REFERENCES users(id) NOT NULL,
        group_type group_type NOT NULL,
        group_id UUID NOT NULL,
        group_role group_role NOT NULL
    );

    CREATE TYPE object_type AS ENUM ('PROJECT', 'SCENE', 'DATASOURCE', 'SHAPE', 'WORKSPACE', 'TEMPLATE', 'ANALYSIS');
    CREATE TYPE subject_type AS ENUM ('ALL', 'PLATFORM', 'ORGANIZATION', 'TEAM', 'USER');
    CREATE TYPE action_type AS ENUM ('VIEW', 'EDIT', 'DEACTIVATE', 'DELETE');

    CREATE TABLE access_control_rules (
        id UUID PRIMARY KEY NOT NULL,
        created_at TIMESTAMP NOT NULL,
        created_by VARCHAR(255) REFERENCES users(id) NOT NULL,
        is_active BOOLEAN DEFAULT true NOT NULL,
        object_type object_type NOT NULL,
        object_id UUID NOT NULL,
        subject_type subject_type NOT NULL,
        subject_id text,
        action_type action_type NOT NULL
    );
    """
    ))
}

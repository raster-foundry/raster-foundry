import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M143 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(143)(
    List(
      sqlu"""
      ALTER TABLE annotation_groups
      DROP CONSTRAINT annotation_groups_project_id_fkey,
      ADD CONSTRAINT annotation_groups_project_id_fkey FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE;

      ALTER TABLE annotations
      DROP CONSTRAINT annotations_annotation_group_fkey,
      DROP CONSTRAINT annotations_project_id_fkey,
      ADD CONSTRAINT annotations_annotation_group_fkey FOREIGN KEY (annotation_group) REFERENCES annotation_groups(id) ON DELETE CASCADE,
      ADD CONSTRAINT annotations_project_id_fkey FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE;

      ALTER TABLE aois
      DROP CONSTRAINT aoi_to_project_id,
      ADD CONSTRAINT aoi_to_project_id FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE;

      ALTER TABLE datasources
      DROP CONSTRAINT datasources_license_name_fkey,
      ADD CONSTRAINT datasources_license_name_fkey FOREIGN KEY (license_name) REFERENCES licenses(short_name) ON DELETE SET NULL;

      ALTER TABLE exports
      DROP CONSTRAINT exports_project_id_fkey,
      DROP CONSTRAINT exports_toolrun_id_fkey,
      ADD CONSTRAINT exports_project_id_fkey FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
      ADD CONSTRAINT exports_toolrun_id_fkey FOREIGN KEY (toolrun_id) REFERENCES tool_runs(id) ON DELETE CASCADE;

      ALTER TABLE organization_features
      DROP CONSTRAINT organization_features_feature_flag_fkey,
      DROP CONSTRAINT organization_features_organization_fkey,
      ADD CONSTRAINT organization_features_feature_flag_fkey FOREIGN KEY (feature_flag) REFERENCES feature_flags(id) ON DELETE CASCADE,
      ADD CONSTRAINT exports_toolrun_id_fkey FOREIGN KEY (organization) REFERENCES organizations(id) ON DELETE CASCADE;

      ALTER TABLE organizations
      DROP CONSTRAINT organizations_platform_id_fkey,
      ADD CONSTRAINT organizations_platform_id_fkey FOREIGN KEY (platform_id) REFERENCES platforms(id) ON DELETE CASCADE;

      ALTER TABLE teams
      DROP CONSTRAINT teams_organization_id_fkey,
      ADD CONSTRAINT teams_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE;
    """
    ))
}

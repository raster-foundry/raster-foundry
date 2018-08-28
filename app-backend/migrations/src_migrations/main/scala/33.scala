import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M33 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(33)(
    List(
      sqlu"""
        INSERT INTO tools (
          id, created_at, modified_at, created_by, modified_by, organization_id,
          title, description, requirements, license, visibility, compatible_data_sources,
          stars
        ) VALUES (
          '7311e8ca-9af7-4fab-b63e-559d2e765388',
          now(),
          now(),
          'default',
          'default',
          '9e2bef18-3f46-426b-a5bd-9913ee1ff840',
          'Normalized Difference Vegetation Index - NDVI',
          'NDVI is calculated from the combination of visible and near-infrared light reflected by vegetation.',
          '1 scene with NIR-1 and red band information.',
          '',
          'PUBLIC',
          '{"Sentinel-2", "Landsat 8"}',
          '4.9'
        ), (
          '401d1967-518f-4a8b-a4d9-36310f94bdf6',
          now(),
          now(),
          'default',
          'default',
          '9e2bef18-3f46-426b-a5bd-9913ee1ff840',
          'Rate of Surface Change',
          'Measures 2-dimensional rate of surface change',
          '1 scene with NIR-1 and red band information.',
          'This tool has a license',
          'PUBLIC',
          '{"Sentinel-2", "Landsat 8"}',
          '4.8'
        ), (
          'a115269a-69b4-49c6-bbcf-289f765be969',
          now(),
          now(),
          'default',
          'default',
          '9e2bef18-3f46-426b-a5bd-9913ee1ff840',
          'Direction of Surface Change',
          'Measures angular direction of surface change.',
          '1 scene with NIR-1 and red band information.',
          '',
          'PUBLIC',
          '{"Sentinel-2", "Landsat 8"}',
          '4.7'
        );

        INSERT INTO tool_categories (
          slug_label, created_at, modified_at, created_by, modified_by, category
        ) VALUES (
          'agriculture',
          now(),
          now(),
          'default',
          'default',
          'Agriculture'
        ), (
          'aggregates',
          now(),
          now(),
          'default',
          'default',
          'Aggregates'
        );

        INSERT INTO tool_categories_to_tools (
          tool_category_slug, tool_id
        ) VALUES (
          'agriculture',
          '7311e8ca-9af7-4fab-b63e-559d2e765388'
        ), (
          'aggregates',
          '401d1967-518f-4a8b-a4d9-36310f94bdf6'
        ), (
          'aggregates',
          'a115269a-69b4-49c6-bbcf-289f765be969'
        );

        INSERT INTO tool_tags (
          id, created_at, modified_at, created_by, modified_by, organization_id, tag
        ) VALUES (
          'cb5df426-321d-49c6-afe0-07f2f185b9db',
          now(),
          now(),
          'default',
          'default',
          '9e2bef18-3f46-426b-a5bd-9913ee1ff840',
          'Vegetation Index'
        ), (
          '38ce33f3-30a4-4870-977e-6847f806aaca',
          now(),
          now(),
          'default',
          'default',
          '9e2bef18-3f46-426b-a5bd-9913ee1ff840',
          'Image Classification'
        );

        INSERT INTO tool_tags_to_tools (
          tool_tag_id, tool_id
        ) VALUES (
          'cb5df426-321d-49c6-afe0-07f2f185b9db',
          '7311e8ca-9af7-4fab-b63e-559d2e765388'
        ), (
          '38ce33f3-30a4-4870-977e-6847f806aaca',
          '401d1967-518f-4a8b-a4d9-36310f94bdf6'
        ), (
          'cb5df426-321d-49c6-afe0-07f2f185b9db',
          'a115269a-69b4-49c6-bbcf-289f765be969'
        ), (
          '38ce33f3-30a4-4870-977e-6847f806aaca',
          'a115269a-69b4-49c6-bbcf-289f765be969'
        );
    """
    ))
}

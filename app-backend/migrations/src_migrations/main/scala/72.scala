import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M72 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(72)(List(
    sqlu"""
        INSERT INTO tools (
          id, owner, created_at, modified_at, created_by, modified_by, organization_id,
          title, description, requirements, license, visibility, compatible_data_sources,
          stars, definition
        ) VALUES (
          '2d3a351f-54b4-42a9-9db4-d027b9aac03c',
          'default',
          now(),
          now(),
          'default',
          'default',
          '9e2bef18-3f46-426b-a5bd-9913ee1ff840',
          'NDWI (Normalized Difference Water Index)',
          'An index that is primarily used to distinguish water bodies.',
          '',
          '',
          'PUBLIC',
          '{}',
          '5.0',
          '{"metadata":{"label":"divide"},"id":"c79b851c-7fca-4767-bdd4-7a725fc2bc9f","apply":"/","args":[{"metadata":{"label":"subtract"},"id":"7084e499-5640-475b-8057-62e1495368bc","apply":"-","args":[{"metadata":{"label":"G"},"type":"src","id":"053277bd-5d7d-48e8-8864-4867b16e0dd6"},{"metadata":{"label":"NIR"},"type":"src","id":"ab19dcd6-6f73-4883-b522-b68e36df4cd8"}]},{"metadata":{"label":"add"},"id":"6a20d2b3-e84c-4e66-8781-f77c31835302","apply":"+","args":[{"metadata":{"label":"G"},"type":"src","id":"053277bd-5d7d-48e8-8864-4867b16e0dd6"},{"metadata":{"label":"NIR"},"type":"src","id":"ab19dcd6-6f73-4883-b522-b68e36df4cd8"}]}]}'
        ), (
          '44fad5c9-1e0d-4631-aaa0-a61182619cb1',
          'default',
          now(),
          now(),
          'default',
          'default',
          '9e2bef18-3f46-426b-a5bd-9913ee1ff840',
          'NDMI (Normalized Difference Moisture Index)',
          'An index that assesses the variation of the moisture content of vegetation.',
          '',
          '',
          'PUBLIC',
          '{}',
          '5.0',
          '{"metadata":{"label":"divide"},"id":"6929460f-0d08-4128-98a4-a7e074251cea","apply":"/","args":[{"metadata":{"label":"subtract"},"id":"6c01af2c-9061-4cc8-bc0e-a44bbdf3639f","apply":"-","args":[{"metadata":{"label":"NIR"},"type":"src","id":"bac1b0f6-0b73-4981-b0ac-b5ee92269cc6"},{"metadata":{"label":"SWIR1"},"type":"src","id":"8c379b55-2c07-4e92-a928-bdc9256b7100"}]},{"metadata":{"label":"add"},"id":"08b571ae-935e-46d3-b3c2-bb16410e6438","apply":"+","args":[{"metadata":{"label":"NIR"},"type":"src","id":"bac1b0f6-0b73-4981-b0ac-b5ee92269cc6"},{"metadata":{"label":"SWIR1"},"type":"src","id":"8c379b55-2c07-4e92-a928-bdc9256b7100"}]}]}'
        ), (
          'cb8d32c2-641d-4f01-b85d-20319dc5a1d2',
          'default',
          now(),
          now(),
          'default',
          'default',
          '9e2bef18-3f46-426b-a5bd-9913ee1ff840',
          'NBR (Normalized Burn Ratio)',
          'An index designed to highlight burned areas and to assess fire severity.',
          '',
          '',
          'PUBLIC',
          '{}',
          '5.0',
          '{"metadata":{"label":"divide"},"id":"a2816828-3e34-42c4-9085-e681acf8de61","apply":"/","args":[{"metadata":{"label":"subtract"},"id":"7f8fdbda-fcc0-4ba7-8193-95741abaed78","apply":"-","args":[{"metadata":{"label":"NIR"},"type":"src","id":"39c2812f-fa5d-435a-b92a-77cf7995a0ad"},{"metadata":{"label":"SWIR2"},"type":"src","id":"7ceb6ca9-774b-4681-adae-ecb3a1bd2d75"}]},{"metadata":{"label":"add"},"id":"4e19f95e-0240-4c18-8360-89f9f7f14103","apply":"+","args":[{"metadata":{"label":"NIR"},"type":"src","id":"39c2812f-fa5d-435a-b92a-77cf7995a0ad"},{"metadata":{"label":"SWIR2"},"type":"src","id":"7ceb6ca9-774b-4681-adae-ecb3a1bd2d75"}]}]}'
        ), (
          '330136a5-1d74-4508-957d-9061ed5676d1',
          'default',
          now(),
          now(),
          'default',
          'default',
          '9e2bef18-3f46-426b-a5bd-9913ee1ff840',
          'NBR2 (Normalized Burn Ratio 2)',
          'An modified version of the normalized burn ratio that highlights sensitivity to water in vegetion.',
          '',
          '',
          'PUBLIC',
          '{}',
          '5.0',
          '{"metadata":{"label":"divide"},"id":"16783459-826e-4952-b01e-6188394f82f3","apply":"/","args":[{"metadata":{"label":"subtract"},"id":"461cf91e-d989-4981-a30b-5ac47ed27684","apply":"-","args":[{"metadata":{"label":"SWIR1"},"type":"src","id":"f9ff7d41-4781-4acd-83e5-8882210511b6"},{"metadata":{"label":"SWIR2"},"type":"src","id":"ece0df75-ac24-4360-b2d4-1dc18862e25e"}]},{"metadata":{"label":"add"},"id":"0c3da658-5a27-4c86-bdb9-7b6242b9bdc3","apply":"+","args":[{"metadata":{"label":"SWIR1"},"type":"src","id":"f9ff7d41-4781-4acd-83e5-8882210511b6"},{"metadata":{"label":"SWIR2"},"type":"src","id":"ece0df75-ac24-4360-b2d4-1dc18862e25e"}]}]}'
        ), (
          '75f70af3-83db-4ff7-b9d9-3f40baae198e',
          'default',
          now(),
          now(),
          'default',
          'default',
          '9e2bef18-3f46-426b-a5bd-9913ee1ff840',
          'SAVI (Soil Adjusted Vegetation Index)',
          'A vegetation index, based on NDVI, that accounts for soil color, moisture, and saturation effects.',
          '',
          '',
          'PUBLIC',
          '{}',
          '5.0',
          '{"metadata":{"label":"multiply"},"id":"880cc8e9-78f3-41cf-b425-300357bb10de","apply":"*","args":[{"metadata":{"label":"divide"},"id":"ada971ef-3b29-4fe3-a5c4-7a3285fb4a13","apply":"/","args":[{"metadata":{"label":"subtract"},"id":"97fda16b-f9ca-4eff-a4c1-a5c14e335a21","apply":"-","args":[{"metadata":{"label":"NIR"},"type":"src","id":"b0a72110-9143-453a-917e-7e0b526d7250"},{"metadata":{"label":"R"},"type":"src","id":"0c474825-4c63-405f-b42b-a45591e5eb8b"}]},{"metadata":{"label":"add"},"id":"2dcbd30e-5aa8-457b-b25e-052614f727f0","apply":"+","args":[{"metadata":{"label":"add"},"id":"50f7e317-58bc-47d6-a69d-053ee17f4e82","apply":"+","args":[{"metadata":{"label":"NIR"},"type":"src","id":"b0a72110-9143-453a-917e-7e0b526d7250"},{"metadata":{"label":"R"},"type":"src","id":"0c474825-4c63-405f-b42b-a45591e5eb8b"}]},{"metadata":{"label":"L"},"type":"const","constant":0.5,"id":"31ea89e1-8b48-4a28-93e6-5dbbf990c537"}]}]},{"metadata":{"label":"add"},"id":"5a0ad1f4-0978-4587-a679-07920fd77abb","apply":"+","args":[{"metadata":{},"id":"337df18b-7e02-40ff-8449-c7342d82dfbc","type":"const","constant":"1"},{"metadata":{"label":"L"},"type":"const","constant":0.5,"id":"31ea89e1-8b48-4a28-93e6-5dbbf990c537"}]}]}'
        ), (
          'ac0bb83e-e8cc-492c-8c9e-5c622079f295',
          'default',
          now(),
          now(),
          'default',
          'default',
          '9e2bef18-3f46-426b-a5bd-9913ee1ff840',
          'BSI (Bare Soil Index)',
          'A soil index that is able to capture soil mineral content variations.',
          '',
          '',
          'PUBLIC',
          '{}',
          '5.0',
          '{"metadata":{"label":"divide"},"id":"330e4c1a-9802-44fc-86a2-757142e473fe","apply":"/","args":[{"metadata":{"label":"subtract"},"id":"8995ee71-ddfa-4ca8-92df-7a4560ddbc2b","apply":"-","args":[{"metadata":{"label":"add"},"id":"ba0a4c41-25da-4c35-9fae-ac217837008b","apply":"+","args":[{"metadata":{"label":"SWIR1"},"type":"src","id":"9298bf69-5bc5-490b-abf1-9a895b1f9842"},{"metadata":{"label":"R"},"type":"src","id":"b1f6d953-3793-45f0-8217-6c7a92daa82e"}]},{"metadata":{"label":"add"},"id":"2a4277a2-79d9-4922-8ece-cc5f8c8a046e","apply":"+","args":[{"metadata":{"label":"NIR"},"type":"src","id":"86fc46c4-4e7a-43d5-bf65-13884c910e6a"},{"metadata":{"label":"B"},"type":"src","id":"3d2eeb1a-8c10-4ca7-b7fc-5a526b8717e8"}]}]},{"metadata":{"label":"add"},"id":"2a7a255a-6ad8-4b6c-afee-eceaa1e193fe","apply":"+","args":[{"metadata":{"label":"add"},"id":"b6d75942-bdbf-4a46-9366-1cdd7cf3f37a","apply":"+","args":[{"metadata":{"label":"SWIR1"},"type":"src","id":"9298bf69-5bc5-490b-abf1-9a895b1f9842"},{"metadata":{"label":"R"},"type":"src","id":"b1f6d953-3793-45f0-8217-6c7a92daa82e"}]},{"metadata":{"label":"add"},"id":"c283a9be-973b-45c2-8830-02544fcb2a9d","apply":"+","args":[{"metadata":{"label":"NIR"},"type":"src","id":"86fc46c4-4e7a-43d5-bf65-13884c910e6a"},{"metadata":{"label":"B"},"type":"src","id":"3d2eeb1a-8c10-4ca7-b7fc-5a526b8717e8"}]}]}]}'
        ), (
          '3d84906c-d6cc-4309-8b73-e482cc08db4d',
          'default',
          now(),
          now(),
          'default',
          'default',
          '9e2bef18-3f46-426b-a5bd-9913ee1ff840',
          'NDSI (Normalized Difference Snow Index)',
          'An index that is able to distinguish snow from cloud-cover.',
          '',
          '',
          'PUBLIC',
          '{}',
          '5.0',
          '{"metadata":{"label":"divide"},"id":"ce129b86-f388-4116-b2f6-4324e445322e","apply":"/","args":[{"metadata":{"label":"subtract"},"id":"b7a4d89c-751e-46f3-a5b9-383cc4b7e550","apply":"-","args":[{"metadata":{"label":"G"},"type":"src","id":"47f4338d-d141-4c3a-8fbd-99d896437de0"},{"metadata":{"label":"SWIR1"},"type":"src","id":"269740c6-5c32-41fd-895b-4e06cead2876"}]},{"metadata":{"label":"add"},"id":"ede78fe5-8c76-4ccc-8455-a5e5c5b1b7d7","apply":"+","args":[{"metadata":{"label":"G"},"type":"src","id":"47f4338d-d141-4c3a-8fbd-99d896437de0"},{"metadata":{"label":"SWIR1"},"type":"src","id":"269740c6-5c32-41fd-895b-4e06cead2876"}]}]}'
        ), (
          '2c8896da-8c0d-4061-b481-68ddb1814e4c',
          'default',
          now(),
          now(),
          'default',
          'default',
          '9e2bef18-3f46-426b-a5bd-9913ee1ff840',
          'NDGI (Normalized Difference Glacier Index)',
          'An index that can be used to map glacial bodies.',
          '',
          '',
          'PUBLIC',
          '{}',
          '5.0',
          '{"metadata":{"label":"divide"},"id":"6646f73b-fb50-4193-baa2-f4bf0604bdfd","apply":"/","args":[{"metadata":{"label":"subtract"},"id":"1f803791-28b8-4b38-b436-649228cea775","apply":"-","args":[{"metadata":{"label":"G"},"type":"src","id":"0cdcfdad-9e21-4715-8722-04fc13d693f0"},{"metadata":{"label":"R"},"type":"src","id":"3ceedfe5-af44-4e9b-a0a7-65fe0aa42d29"}]},{"metadata":{"label":"add"},"id":"bd690d9c-855a-483d-b8aa-03687714848e","apply":"+","args":[{"metadata":{"label":"G"},"type":"src","id":"0cdcfdad-9e21-4715-8722-04fc13d693f0"},{"metadata":{"label":"R"},"type":"src","id":"3ceedfe5-af44-4e9b-a0a7-65fe0aa42d29"}]}]}'
        ), (
          '5f07de89-0bf3-466c-adac-a64857d36637',
          'default',
          now(),
          now(),
          'default',
          'default',
          '9e2bef18-3f46-426b-a5bd-9913ee1ff840',
          'NPCRI (Normalized Pigment Chlorophyll Ratio Index)',
          'A vegetation index designed to respond to the chlorophyll content of vegetation. It can be used to determine various vegetation parameters.',
          '',
          '',
          'PUBLIC',
          '{}',
          '5.0',
          '{"metadata":{"label":"divide"},"id":"e43e0a38-6765-4111-baa2-01be48aefec2","apply":"/","args":[{"metadata":{"label":"subtract"},"id":"edc85db3-8edc-4382-be36-d8a24601b9ce","apply":"-","args":[{"metadata":{"label":"R"},"type":"src","id":"1e55b0dc-928b-4316-a813-f8e9ad5cefc3"},{"metadata":{"label":"B"},"type":"src","id":"d5518a81-8681-47d6-b10c-460156c75e6a"}]},{"metadata":{"label":"add"},"id":"50a949cd-e298-4e76-9285-5652dd363b32","apply":"+","args":[{"metadata":{"label":"R"},"type":"src","id":"1e55b0dc-928b-4316-a813-f8e9ad5cefc3"},{"metadata":{"label":"B"},"type":"src","id":"d5518a81-8681-47d6-b10c-460156c75e6a"}]}]}'
        ), (
          '6c9f3c72-0552-4a6d-adee-b1a350375a85',
          'default',
          now(),
          now(),
          'default',
          'default',
          '9e2bef18-3f46-426b-a5bd-9913ee1ff840',
          'EVI (Enhanced Vegetation Index)',
          'A vegetation index that corrects for both soil and atmospheric effects and is designed to be resistant to saturation effects of dense green vegetation.',
          '',
          '',
          'PUBLIC',
          '{}',
          '5.0',
          '{"metadata":{"label":"multiply"},"id":"78b223d6-9c97-49b3-872e-e769a2acef5f","apply":"*","args":[{"metadata":{"label":"G"},"type":"const","constant":2.5,"id":"8b757337-921f-47fb-ad5f-102936060186"},{"metadata":{"label":"divide"},"id":"1251841a-a2dc-4018-9529-6f43b54038ac","apply":"/","args":[{"metadata":{"label":"subtract"},"id":"7cd4790e-91d7-4724-9655-75afdcf6853f","apply":"-","args":[{"metadata":{"label":"NIR"},"type":"src","id":"46e5952c-0790-4601-8ed1-f9e890e57bce"},{"metadata":{"label":"R"},"type":"src","id":"1dbd42f1-7eee-47b8-9ebe-6bb4cf329f9b"}]},{"metadata":{"label":"add"},"id":"385e48f6-2c1f-4a6f-8997-fc585dbb7d71","apply":"+","args":[{"metadata":{"label":"subtract"},"id":"9884bc14-d127-4af0-9194-a034b278f1f8","apply":"-","args":[{"metadata":{"label":"add"},"id":"2edf94d7-a402-4141-b1f3-d7289180d85a","apply":"+","args":[{"metadata":{"label":"NIR"},"type":"src","id":"46e5952c-0790-4601-8ed1-f9e890e57bce"},{"metadata":{"label":"multiply"},"id":"3471abfb-3c5c-46ce-88f7-17a2dc1ce458","apply":"*","args":[{"metadata":{"label":"C1"},"type":"const","constant":6,"id":"96cc3480-eb5c-4eb4-ac89-ebbc8e1776ef"},{"metadata":{"label":"R"},"type":"src","id":"1dbd42f1-7eee-47b8-9ebe-6bb4cf329f9b"}]}]},{"metadata":{"label":"multiply"},"id":"5720e9e8-3921-40bb-957c-5017c39a8fc7","apply":"*","args":[{"metadata":{"label":"C2"},"type":"const","constant":7.5,"id":"233d2c13-5663-4251-81bd-b80a4ff3c4c0"},{"metadata":{"label":"B"},"type":"src","id":"1c7c068e-050d-420f-97df-38f139a4224c"}]}]},{"metadata":{"label":"L"},"type":"const","constant":1,"id":"650502b5-7cfe-4806-9064-908da1548c15"}]}]}]}'
        );
    """
  ))
}

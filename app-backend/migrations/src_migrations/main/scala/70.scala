import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M70 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(70)(
    List(
      sqlu"""
        INSERT INTO tools (
          id, owner, created_at, modified_at, created_by, modified_by, organization_id,
          title, description, requirements, license, visibility, compatible_data_sources,
          stars, definition
        ) VALUES (
          'd356321c-5a9b-47ce-8456-2af6bd724b58',
          'default',
          now(),
          now(),
          'default',
          'default',
          '9e2bef18-3f46-426b-a5bd-9913ee1ff840',
          'Phycocyanin detection',
          'A specialized formula for highlighting high concentrations of phycocyanin (useful for algal bloom detection). Values >5.93 can be classified as likely algal blooms.',
          'Landsat bands 2, 3, 4, 5, 6, and 7.',
          '',
          'PUBLIC',
          '{"Landsat 8"}',
          '5.0',
          '{"apply":"+","args":[{"type":"const","id":"be988b9b-e1a5-3b6d-bae7-c5a1366bb4d9","constant":47.7,"metadata":null},{"apply":"*","args":[{"type":"const","id":"388e5b0f-5e51-383a-82a0-fbc14289a270","constant":-9.21,"metadata":null},{"apply":"/","args":[{"type":"src","id":"3e1f1b29-f719-31a9-aeb8-21b4b9fb429e","metadata":{"label":"Landsat Near Infrared (NIR)","description":"Wavelength: 0.76-0.90 μm","histogram":null,"colorRamp":null,"classMap":null}},{"type":"src","id":"be5ddbc5-900c-3d61-a808-5d5457ba34af","metadata":{"label":"Landsat Green","description":"Wavelength: 0.52-0.60 μm","histogram":null,"colorRamp":null,"classMap":null}}],"id":"50eb31a0-d6f1-34ac-a9e6-a3ce0ad5c4a8","metadata":null}],"id":"ac37b87a-63c7-3019-b725-3509ad84249a","metadata":null},{"apply":"*","args":[{"type":"const","id":"b686247e-dfcf-3edc-bd9b-3a59a031682f","constant":29.7,"metadata":null},{"apply":"/","args":[{"type":"src","id":"07e5098f-53b9-39ad-ae37-8b146e34de19","metadata":{"label":"Landsat Shortwave Infrared (SWIR) 1","description":"Wavelength: 1.55-1.75 μm","histogram":null,"colorRamp":null,"classMap":null}},{"type":"src","id":"be5ddbc5-900c-3d61-a808-5d5457ba34af","metadata":{"label":"Landsat Green","description":"Wavelength: 0.52-0.60 μm","histogram":null,"colorRamp":null,"classMap":null}}],"id":"531b2c25-731e-3fd1-9196-11b092c465cd","metadata":null}],"id":"6a096772-c909-3745-a53d-3bbd5891109f","metadata":null},{"apply":"*","args":[{"type":"const","id":"27d00f8f-525d-3798-bb93-f60139a8c659","constant":-118.0,"metadata":null},{"apply":"/","args":[{"type":"src","id":"07e5098f-53b9-39ad-ae37-8b146e34de19","metadata":{"label":"Landsat Shortwave Infrared (SWIR) 1","description":"Wavelength: 1.55-1.75 μm","histogram":null,"colorRamp":null,"classMap":null}},{"type":"src","id":"3e1f1b29-f719-31a9-aeb8-21b4b9fb429e","metadata":{"label":"Landsat Near Infrared (NIR)","description":"Wavelength: 0.76-0.90 μm","histogram":null,"colorRamp":null,"classMap":null}}],"id":"7a86d957-fee7-3f98-874b-8bce383d2b45","metadata":null}],"id":"965ca2de-f9b0-3e3c-890c-e4a3b1cc29ae","metadata":null},{"apply":"*","args":[{"type":"const","id":"af07da0e-ab96-3101-a766-1857bf924f50","constant":-6.81,"metadata":null},{"apply":"/","args":[{"type":"src","id":"9a35ab4e-a801-3bb2-be08-7c040636ac3f","metadata":{"label":"Landsat Thermal","description":"Wavelength: 10.40-12.50 μm","histogram":null,"colorRamp":null,"classMap":null}},{"type":"src","id":"3e1f1b29-f719-31a9-aeb8-21b4b9fb429e","metadata":{"label":"Landsat Near Infrared (NIR)","description":"Wavelength: 0.76-0.90 μm","histogram":null,"colorRamp":null,"classMap":null}}],"id":"076f4e63-05a7-391e-97e6-a18f1db600af","metadata":null}],"id":"92c2f02a-5e4b-352a-b1ca-12f4df89398b","metadata":null},{"apply":"*","args":[{"type":"const","id":"fdfbb776-28e2-3ef8-9156-4acd92b9d196","constant":41.9,"metadata":null},{"apply":"/","args":[{"type":"src","id":"b71207fc-9102-3266-963b-52d2849f034c","metadata":{"label":"Landsat Shortwave Infrared (SWIR) 2","description":"Wavelength: 2.08-2.35 μm","histogram":null,"colorRamp":null,"classMap":null}},{"type":"src","id":"3e1f1b29-f719-31a9-aeb8-21b4b9fb429e","metadata":{"label":"Landsat Near Infrared (NIR)","description":"Wavelength: 0.76-0.90 μm","histogram":null,"colorRamp":null,"classMap":null}}],"id":"94d52e12-3279-3b88-b24d-bcd42937674e","metadata":null}],"id":"81df3fb1-d9d5-3944-a7aa-ac6203498b6b","metadata":null},{"apply":"*","args":[{"type":"const","id":"9f4326da-a635-3519-93da-1a797c155dca","constant":-14.7,"metadata":null},{"apply":"/","args":[{"type":"src","id":"b71207fc-9102-3266-963b-52d2849f034c","metadata":{"label":"Landsat Shortwave Infrared (SWIR) 2","description":"Wavelength: 2.08-2.35 μm","histogram":null,"colorRamp":null,"classMap":null}},{"type":"src","id":"07e5098f-53b9-39ad-ae37-8b146e34de19","metadata":{"label":"Landsat Shortwave Infrared (SWIR) 1","description":"Wavelength: 1.55-1.75 μm","histogram":null,"colorRamp":null,"classMap":null}}],"id":"9dd804ec-c721-38db-b76c-b27f9dd53aa5","metadata":null}],"id":"29ae7563-a1da-3e88-a345-1b3a7f3ca417","metadata":null}],"id":"34ec78fc-c91f-3b1e-94cd-85e4a0924332","metadata":{"label":"Phycocyanin Detection","description":"http://www.sciencedirect.com/science/article/pii/S0034425716304928","histogram":null,"colorRamp":null,"classMap":null}}'
        ), (
          '4168fc9a-d3d8-4182-8b24-53c5bdaec67b',
          'default',
          now(),
          now(),
          'default',
          'default',
          '9e2bef18-3f46-426b-a5bd-9913ee1ff840',
          'Near Infrared with Simple Atmospheric Correction',
          'Used to estimate algal blooms. Values > 0.0235 can be classified as likely algal blooms.',
          'Landsat NIR and SWI-1 bands (4 and 5 respectively).',
          '',
          'PUBLIC',
          '{"Landsat 8"}',
          '4.8',
          '{"apply":"-","args":[{"type":"src","id":"07e5098f-53b9-39ad-ae37-8b146e34de19","metadata":{"label":"Landsat Shortwave Infrared (SWIR) 1","description":"Wavelength: 1.55-1.75 μm","histogram":null,"colorRamp":null,"classMap":null}},{"apply":"*","args":[{"type":"const","id":"ca942292-5c8f-3fa0-8431-84f8536196c3","constant":-1.03,"metadata":null},{"type":"src","id":"9a35ab4e-a801-3bb2-be08-7c040636ac3f","metadata":{"label":"Landsat Thermal","description":"Wavelength: 10.40-12.50 μm","histogram":null,"colorRamp":null,"classMap":null}}],"id":"50eb31a0-d6f1-34ac-a9e6-a3ce0ad5c4a8","metadata":null}],"id":"1b52eb8e-f2c1-375c-bdf3-ffbe9e3c05da","metadata":{"label":"Improved NIR with SAC","description":"The most performant index from http://www.sciencedirect.com/science/article/pii/S0034425716304928","histogram":null,"colorRamp":null,"classMap":null}}'
        ), (
          '78408b1a-200a-4868-a7ed-2a0d0db402ee',
          'default',
          now(),
          now(),
          'default',
          'default',
          '9e2bef18-3f46-426b-a5bd-9913ee1ff840',
          'NIR over Red with Simple Atmospheric Correction',
          'Used to estimate algal blooms. Values > 0.495 can be classified as likely algal blooms.',
          '1 scene with NIR-1 and red band information.',
          '',
          'PUBLIC',
          '{"Sentinel-2", "Landsat 8"}',
          '4.7',
          '{"apply":"/","args":[{"apply":"-","args":[{"type":"src","id":"3e1f1b29-f719-31a9-aeb8-21b4b9fb429e","metadata":{"label":"Landsat Near Infrared (NIR)","description":"Wavelength: 0.76-0.90 μm","histogram":null,"colorRamp":null,"classMap":null}},{"type":"src","id":"07e5098f-53b9-39ad-ae37-8b146e34de19","metadata":{"label":"Landsat Shortwave Infrared (SWIR) 1","description":"Wavelength: 1.55-1.75 μm","histogram":null,"colorRamp":null,"classMap":null}}],"id":"1b52eb8e-f2c1-375c-bdf3-ffbe9e3c05da","metadata":null},{"apply":"-","args":[{"type":"src","id":"e9bab2e8-a014-3cb1-9171-e84f1f31267b","metadata":{"label":"Landsat Red","description":"Wavelength: 0.63-0.69 μm","histogram":null,"colorRamp":null,"classMap":null}},{"type":"src","id":"07e5098f-53b9-39ad-ae37-8b146e34de19","metadata":{"label":"Landsat Shortwave Infrared (SWIR) 1","description":"Wavelength: 1.55-1.75 μm","histogram":null,"colorRamp":null,"classMap":null}}],"id":"5fbef652-69a9-3bdd-8210-6251dd89b1dc","metadata":null}],"id":"50eb31a0-d6f1-34ac-a9e6-a3ce0ad5c4a8","metadata":{"label":"NIR over red, with SAC","description":"http://www.sciencedirect.com/science/article/pii/S0034425716304928","histogram":null,"colorRamp":null,"classMap":null}}'
        );
    """
    ))
}

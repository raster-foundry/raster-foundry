# Change Log
## [Unreleased](https://github.com/raster-foundry/raster-foundry/tree/develop)

### Added
- Added backend support for rendering tiles and fetching histograms for analyses with project layers at their leaves [\#4603](https://github.com/raster-foundry/raster-foundry/pull/4603)
- Added scene counts to project layer items [\#4625](https://github.com/raster-foundry/raster-foundry/pull/4625)
- Added project analyses list view [\#4585](https://github.com/raster-foundry/raster-foundry/pull/4585)
- Added backend support for project layer async exports [\#4619](https://github.com/raster-foundry/raster-foundry/pull/4619)
- Added front-end support for importing to project layers [\#4646](https://github.com/raster-foundry/raster-foundry/pull/4646)

### Changed

- Updated default project layer color group hex code [\#4616](https://github.com/raster-foundry/raster-foundry/pull/4616)
- Updated `gdal-js` and `requests` [\#4618](https://github.com/raster-foundry/raster-foundry/pull/4618)
- Swap trash icon for "Remove" text on scene item components [\#4621](https://github.com/raster-foundry/raster-foundry/pull/4621)

### Deprecated

### Removed

- Removed layer re-ordering, layer sorting, layer type selection from UI [\#4616](https://github.com/raster-foundry/raster-foundry/pull/4616)

### Fixed

- Unified coloring for scene detail modal scene preview and map scene preview [\#4630](https://github.com/raster-foundry/raster-foundry/pull/4630)

## [1.18.0](https://github.com/raster-foundry/raster-foundry/tree/1.18.0) (2019-02-11)

### Added

- Added new export utility as replacement for the old (non-functioning) spark-based export [\#4589](https://github.com/raster-foundry/raster-foundry/pull/4589)
- Added project layer creation workflow's modal on UI [\#4575](https://github.com/raster-foundry/raster-foundry/pull/4575)
- Added project layer Annotation related endpoints [\#4569](https://github.com/raster-foundry/raster-foundry/pull/4569)
- CRUDL endpoints for project layer scenes [\#4550](https://github.com/raster-foundry/raster-foundry/pull/4550)
- Added publishing of lambda functions to CI build process [\#4586](https://github.com/raster-foundry/raster-foundry/pull/4586)
- Added tile server support for masked analyses [\#4571](https://github.com/raster-foundry/raster-foundry/pull/4571)
- Added project layer navigation bar [\#4581](https://github.com/raster-foundry/raster-foundry/pull/4581)
- Added layer parameter to /api/scenes and added inLayer property to scene browse responses [\#4615](https://github.com/raster-foundry/raster-foundry/pull/4615)

### Fixed

- Removed unused imports and assignments [\#4579](https://github.com/raster-foundry/raster-foundry/pull/4579)
- Included geometry filter in backsplash scene service to prevent erroneous 500s [\#4580](https://github.com/raster-foundry/raster-foundry/pull/4580)
- Made scapegoat less angry [\#4611](https://github.com/raster-foundry/raster-foundry/pull/4611)
- Set hasNext correctly on /api/scenes when there are more than 100 scenes [\#4615](https://github.com/raster-foundry/raster-foundry/pull/4615)
- Use sane default when the accepted query parameter is not set on /api/project/{}/layer/{}/scenes [\#4615](https://github.com/raster-foundry/raster-foundry/pull/4615)

### Security

## [1.17.1](https://github.com/raster-foundry/raster-foundry/tree/1.17.1) (2019-02-04)

### Fixed
- Removed references from old tileserver from Jenkinsfile.release

## [1.17.0](https://github.com/raster-foundry/raster-foundry/tree/1.17.0) (2019-02-04)

### Added
- Templates can now be shared and filtered by ownership [\#4357](https://github.com/raster-foundry/raster-foundry/pull/4357)
- Added ProjectLayer datamodel, dao, and migration [\#4460](https://github.com/raster-foundry/raster-foundry/pull/4460)
- Added lambda function for reactively processing new Landsat 8 imagery [\#4471](https://github.com/raster-foundry/raster-foundry/pull/4471)
- Added lambda function for reactively processing new Sentinel-2 imagery [\#4491](https://github.com/raster-foundry/raster-foundry/pull/4491)
- Added a migration that creates relationship among projects, project layers, and scenes and populates corresponding tables. Updated associated data models and `Dao`s [\#4479](https://github.com/raster-foundry/raster-foundry/pull/4479)
- CRUDL endpoints for project layers [\#4512](https://github.com/raster-foundry/raster-foundry/pull/4512)
- Added project layer mosaic and scene order endpoint [\#4547](https://github.com/raster-foundry/raster-foundry/pull/4547)
- Added `SceneToLayer` data model and `SceneToLayerDao`; updated related function calls in `ProjectDao` and project api [\#4513](https://github.com/raster-foundry/raster-foundry/pull/4513)
- Added a migration that creates relationship among projects, project layers, and scenes and populates corresponding tables. Updated associated data models and `Dao`s. [\#4479](https://github.com/raster-foundry/raster-foundry/pull/4479)
- Allow sharing most objects when you have edit permissions granted to you [\#4514](https://github.com/raster-foundry/raster-foundry/pull/4514)
- Added TMS route for project layers [\#4523](https://github.com/raster-foundry/raster-foundry/pull/4523)
- Added TMS, quick export, and histogram routes for project layers [\#4523](https://github.com/raster-foundry/raster-foundry/pull/4523), [\#4553](https://github.com/raster-foundry/raster-foundry/pull/4553)
- Added project, project layer, and template ID fields to tool runs for later filtering [\#4546](https://github.com/raster-foundry/raster-foundry/pull/4546) and to API routes as filter fields [\#4551](https://github.com/raster-foundry/raster-foundry/pull/4551)
- Added project layer mosaic and scene order endpoint [\#4547](https://github.com/raster-foundry/raster-foundry/pull/4547)
- Add Layer ID to Annotations and Annotation Groups [\#4558](https://github.com/raster-foundry/raster-foundry/pull/4558)
- Support uploads to project layers on the API [#\4524](https://github.com/raster-foundry/raster-foundry/pull/4524)

### Changed
- Reorganized project structure to simplify dependency graph (`tool` was mostly removed; `tool`s still-relevant pieces, `bridge`, and `datamodel` moved into the project `common`) [\#4564](https://github.com/raster-foundry/raster-foundry/pull/4564)
- Only analyses owned by the current user are displayed in the analysis browsing UI [\#4357](https://github.com/raster-foundry/raster-foundry/pull/4357)
- Updated permission check logic for lab templates to make ownership filter work as expected [\#4462](https://github.com/raster-foundry/raster-foundry/pull/4462)
- Unify S3 client interface usage [\#4441](https://github.com/raster-foundry/raster-foundry/pull/4441)
- Moved common authentication logic to http4s-util subproject [\#4496](https://github.com/raster-foundry/raster-foundry/pull/4496)
- Upgraded scala, javascript, and python rollbar clients [\#4502](https://github.com/raster-foundry/raster-foundry/pull/4502)
- Added ability to download images as part of development environment setup [\#4509](https://github.com/raster-foundry/raster-foundry/pull/4509)
- Allow users with edit permissions to edit the permissions of objects [\#4490](https://github.com/raster-foundry/raster-foundry/pull/4490)

### Deprecated

### Removed
- Removed unused dependency `geotrellis-raster-testkit`[\#4482](https://github.com/raster-foundry/raster-foundry/pull/4482)
- Removed legacy tile server subproject and configuration [\#4478](https://github.com/raster-foundry/raster-foundry/pull/4478)
- Removed unused metrics collection resources and application code [\#4475](https://github.com/raster-foundry/raster-foundry/pull/4475), [\#4493](https://github.com/raster-foundry/raster-foundry/pull/4493)
- Removed deprecated Gatling load tests [\#4504](https://github.com/raster-foundry/raster-foundry/pull/4504)

### Fixed
- Shapes drawn within the scene search filter context can now be saved [\#4474](https://github.com/raster-foundry/raster-foundry/pull/4474)
- Mosaics are again constructed with rasters instead of with IO[rasters] [\#4498](https://github.com/raster-foundry/raster-foundry/pull/4498)
- Improved healthcheck logic in backsplash healthcheck endpoint [\#4548](https://github.com/raster-foundry/raster-foundry/pull/4548)
- Fixed bug for publishing project page [\#4578](https://github.com/raster-foundry/raster-foundry/pull/4578)

### Security
- Upgrade webpack-dev-server to address vulnerability (https://nvd.nist.gov/vuln/detail/CVE-2018-14732) [\#4476](https://github.com/raster-foundry/raster-foundry/pull/4476)

## [1.16.4](https://github.com/raster-foundry/raster-foundry/tree/1.16.4) (2019-01-21)

- Sanitized more user fields in search endpoint [\#4505](https://github.com/raster-foundry/raster-foundry/pull/4505) and in platform member list and organization search [\#4506](https://github.com/raster-foundry/raster-foundry/pull/4506)

## [1.16.3](https://github.com/raster-foundry/raster-foundry/tree/1.16.3) (2019-01-17)

### Fixed
- Made backsplash respect the RF_LOG_LEVEL environment variable [\#4483](https://github.com/raster-foundry/raster-foundry/pull/4483)

## [1.16.2](https://github.com/raster-foundry/raster-foundry/tree/1.16.2) (2019-01-14)

### Added
- Added caching for histograms, rastersources, and tiles [\#4429](https://github.com/raster-foundry/raster-foundry/pull/4429)[\#4437](https://github.com/raster-foundry/raster-foundry/pull/4437)
- Made Raster Source configurable via environment variables to more easily test GDAL and GeoTiff Raster Sources [\#4440](https://github.com/raster-foundry/raster-foundry/pull/4440)
- Parallelized mosaic construction from backsplash images [\#4463](https://github.com/raster-foundry/raster-foundry/pull/4463)

### Changed

### Fixed

- Removed faulty no-data interpretation in single band visualization [\#4433](https://github.com/raster-foundry/raster-foundry/pull/4433)
- Fixed histogram calcuation + sampling logic and analysis rendering [\#4438](https://github.com/raster-foundry/raster-foundry/pull/4438)

### Removed

### Fixed

- Added back the scenes mosaic endpoint [\#4439](https://github.com/raster-foundry/raster-foundry/pull/4439)
- Fixed quick export of projects and analyses [\#4459](https://github.com/raster-foundry/raster-foundry/pull/4459)
- Fixed route matching for map token authorization for analyses [\#4463](https://github.com/raster-foundry/raster-foundry/pull/4463)
- Fixed permission checks for project and project datasource in lab analyses [\#4466](https://github.com/raster-foundry/raster-foundry/pull/4466)

### Security

## [1.16.0](https://github.com/raster-foundry/raster-foundry/tree/1.16.0) (2019-01-02)

## Important Notice

- In this release, we have added more fields to platform email settings to make it work better. We strongly suggest platform admins to go to your platform email settings and fill in **all fields**.

### Added
- Templates can now be shared and filtered by ownership [\#4357](https://github.com/raster-foundry/raster-foundry/pull/4357)
- Added a support email field to platform email settings [\#4353](https://github.com/raster-foundry/raster-foundry/pull/4353)
- Supported team creation on user's team list page [\#4345](https://github.com/raster-foundry/raster-foundry/pull/4345)
- Use java's gdal bindings for tile IO in backsplash and better separate concerns between fetching imagery and talking to the database / users [\#4339](https://github.com/raster-foundry/raster-foundry/pull/4339)
- Added dropwizard metrics instrumentation to backsplash methods and endpoints [\#4381](https://github.com/raster-foundry/raster-foundry/pull/4381)
- Added script for ad hoc tile server load testing [\#4395](https://github.com/raster-foundry/raster-foundry/pull/4395), [\#4404](https://github.com/raster-foundry/raster-foundry/pull/4404)
- Added graphite reporter to dropwizard metrics [\#4398](https://github.com/raster-foundry/raster-foundry/pull/4398)
- Added alternative development runner/setup for testing API server and backsplash [\#4402](https://github.com/raster-foundry/raster-foundry/pull/4402)
- Added configuration and helper script for gatling integration test results [\#4410](https://github.com/raster-foundry/raster-foundry/pull/4410)
- Added gatling tests script that can be run in CI [\#4424](https://github.com/raster-foundry/raster-foundry/pull/4424)
- Created AuthedAutoSlash middleware to make authentication and route matching cooperate [\#4425](https://github.com/raster-foundry/raster-foundry/pull/4425)

### Changed

- Only analyses owned by the current user are displayed in the analysis browsing UI [\#4357](https://github.com/raster-foundry/raster-foundry/pull/4357)
- Reorganized scala dependencies for package cleanliness and smaller bundles [\#4301](https://github.com/raster-foundry/raster-foundry/pull/4301)
- If users are not requesting their own info, the returned other users' personal info are protected [\#4360](https://github.com/raster-foundry/raster-foundry/pull/4360)
- Changed the data model of the return of `users/me/roles` endpoint [\#4375](https://github.com/raster-foundry/raster-foundry/pull/4375)
- Added more aggressive timeout to backsplash for improved thread recovery [\#4383](https://github.com/raster-foundry/raster-foundry/pull/4383)
- Decreased max classfile name length from 100 to 70 for CI reasons [\#4388](https://github.com/raster-foundry/raster-foundry/pull/4388)
- Used a fixed number of threadPool for Backsplash [\#4389](https://github.com/raster-foundry/raster-foundry/pull/4389)
- Made timeout length and number of threadPool configurable [\#4389](https://github.com/raster-foundry/raster-foundry/pull/4389)
- Changed to use Fiber for threading in Backsplash services [\#4396](https://github.com/raster-foundry/raster-foundry/pull/4396)
- Ignored errors from integration tests so that reports will always be written to s3 [\#4406](https://github.com/raster-foundry/raster-foundry/pull/4406)
- Changed how the database transactor is passed to backsplash and API servers to prevent accidentally passing implicit execution contexts where they are not wanted [\#4415](https://github.com/raster-foundry/raster-foundry/pull/4415)
- Added ability to test against several projects in gatling integration tests [\#4416](https://github.com/raster-foundry/raster-foundry/pull/4416)
- Changed how to configure threadpools for backsplash and hikari [\#4420](https://github.com/raster-foundry/raster-foundry/pull/4420)

### Fixed

- Made the status code for aoi creation on projects a 201 instead of a 200 [\#4331](https://github.com/raster-foundry/raster-foundry/pull/4331)
- Opened access for group members to remove their group memberships [\#4358](https://github.com/raster-foundry/raster-foundry/pull/4358)
- Used the correct field as outgoing email source in platform email settings [\#4353](https://github.com/raster-foundry/raster-foundry/pull/4353)
- Fix deprecated use of route change listeners which caused window title to break [\#4365](https://github.com/raster-foundry/raster-foundry/pull/4365)
- Fix project ownership filter persistance across pages [\#4376](https://github.com/raster-foundry/raster-foundry/pull/4376)
- Fix logo on project share page and add error handling [/#4377](https://github.com/raster-foundry/raster-foundry/pull/4377)
- Address a number of unhandled promise chains on the frontend [\#4380](https://github.com/raster-foundry/raster-foundry/pull/4380)
- Restored routes missing from backsplash after reintegration into RF main [\#4382](https://github.com/raster-foundry/raster-foundry/pull/4382)
- Restored color correction [\#4387](https://github.com/raster-foundry/raster-foundry/pull/4387)
- Fix logo on project share page and add error handling [/#4377](https://github.com/raster-foundry/raster-foundry/pull/4377)
- Address a number of unhandled promise chains on the frontend [\#4380](https://github.com/raster-foundry/raster-foundry/pull/4380)
- Restored auth and error-handling [\#4390](https://github.com/raster-foundry/raster-foundry/pull/4390)
- Aligned backsplash dockerfile with existing services [\#4394](https://github.com/raster-foundry/raster-foundry/pull/4394)
- Upgraded geotrellis-server to handle thread safety issue that was causing SEGFAULTs in backsplash and for ArrayTile vs. Tile issues [\#4399](https://github.com/raster-foundry/raster-foundry/pull/4399), [\#4412](https://github.com/raster-foundry/raster-foundry/pull/4412), [\#4426](https://github.com/raster-foundry/raster-foundry/pull/4426)
- Switched back to geotrellis for IO to shrink the space of failure conditions [\#4414](https://github.com/raster-foundry/raster-foundry/pull/4414)
- Fetch histograms for multiband mosaics from the database again [\#4417](https://github.com/raster-foundry/raster-foundry/pull/4417)
- Made single band tiles render without weird stripes [\#4423](https://github.com/raster-foundry/raster-foundry/pull/4423)
- Fetched histograms from the database for single band visualization, falling back to from tiles [\#4426](https://github.com/raster-foundry/raster-foundry/pull/4426)

## [1.15.0](https://github.com/raster-foundry/raster-foundry/tree/1.15.0) (2018-11-30)

### Added
- Add Ansible role to install Nexus Repo Manager [\#4277](https://github.com/raster-foundry/raster-foundry/pull/4277)
- Added S3 path suggestions in scene import modal when users upload imageries from S3 buckets [\#4290](https://github.com/raster-foundry/raster-foundry/pull/4290)
- Enabled deleting lab templates on the frontend [\#4287](https://github.com/raster-foundry/raster-foundry/pull/4287)
- Added support for viewing public projects using backsplash [\#4299](https://github.com/raster-foundry/raster-foundry/pull/4299)
- Added script for reprocessing sentinel 2 scenes which were imported with the wrong number of bands [\4349](https://github.com/raster-foundry/raster-foundry/pull/4349

### Changed
- Populate user profiles from their identity tokens more intelligently [\#4298](https://github.com/raster-foundry/raster-foundry/pull/4298)
- Improved project selection modal within the lab [\#4324](https://github.com/raster-foundry/raster-foundry/pull/4324)
- Upgraded to Webpack 4 and replace broken libraries / componenets [\#4199](https://github.com/raster-foundry/raster-foundry/pull/4199)
- Greatly reduce bundle size by using dynamic imports and using a more effective build process [\#4199](https://github.com/raster-foundry/raster-foundry/pull/4199)

### Removed

- Removed email form validation from platform email settings page [\#4294](https://github.com/raster-foundry/raster-foundry/pull/4294)

### Fixed

- Ensured tiles of non-standard sizes get resampled to the appropriate size before reaching users [\#4281](https://github.com/raster-foundry/raster-foundry/pull/4281)
- Specifically handled bad paths to COGs when users create scenes [\#4295](https://github.com/raster-foundry/raster-foundry/pull/4295)
- Fixed shapefile annotations export [\#4300](https://github.com/raster-foundry/raster-foundry/pull/4300)
- Made s3 client tolerate buckets outside of its configured region [\#4319](https://github.com/raster-foundry/raster-foundry/pull/4319)
- Fixed logging dependency stack to eliminate painfully verbose logging in backsplash [\#4326](https://github.com/raster-foundry/raster-foundry/pull/4326)
- Fix thumbnail loading placeholder size [\#4355](https://github.com/raster-foundry/raster-foundry/pull/4355)
- Fix hidden text field for scene image sources [\#4355](https://github.com/raster-foundry/raster-foundry/pull/4355)
- Fix long source names for scenes [\#4355](https://github.com/raster-foundry/raster-foundry/pull/4355)
- Duplicate ingest emails for users with inactive platform UGRs [\#4359](https://github.com/raster-foundry/raster-foundry/pull/4359)

## [1.14.2](https://github.com/raster-foundry/raster-foundry/tree/1.14.2) (2018-11-19)

### Fixed

- Fixed regressions causing non-termination of export and Landsat 8 import jobs [\#4312](https://github.com/raster-foundry/raster-foundry/pull/4312), [\#4313](https://github.com/raster-foundry/raster-foundry/pull/4313)

## [1.14.1](https://github.com/raster-foundry/raster-foundry/tree/1.14.1) (2018-11-13)

### Fixed

- Upgraded maml to 0.0.15 and circe to 0.10.0 and make async jobs use cats-effect IOApp [\#4288](https://github.com/raster-foundry/raster-foundry/pull/4288)
- Repaired short-lived infinite recursion in export async job [\#4297](https://github.com/raster-foundry/raster-foundry/pull/4297)

## [1.14.0](https://github.com/raster-foundry/raster-foundry/tree/1.14.0) (2018-11-08)

### Added
- Added summary endpoint for annotation groups to list the number of labels with different qualities (YES, NO, MISS, UNSURE) to support annotation applications [\#4221](https://github.com/raster-foundry/raster-foundry/pull/4221)
- Added project histogram support for COG and Avro scenes in backsplash [\#4190](https://github.com/raster-foundry/raster-foundry/pull/4190)
- Added map token and authorization header authentication to backsplash [\#4271](https://github.com/raster-foundry/raster-foundry/pull/4271)
- Added project quick png export support to backsplash [\#4273](https://github.com/raster-foundry/raster-foundry/pull/4273)
- Added service-level and total error-handling to backsplash tile server [\#4258](https://github.com/raster-foundry/raster-foundry/pull/4258)
- Administration
  - Allow platforms to set a "From" email field in order to change notification "From" name [#\4214](https://github.com/raster-foundry/raster-foundry/pull/4214)
  - Allow platform administrators to create uploads for other users within their platforms [\#4237](https://github.com/raster-foundry/raster-foundry/pull/4237)

### Changed
- Small text edit to the Imports page [\#4198](https://github.com/raster-foundry/raster-foundry/pull/4198)
- Updated package and assembly jar names [\#3924](https://github.com/raster-foundry/raster-foundry/pull/3924), [\#4222](https://github.com/raster-foundry/raster-foundry/pull/4222), [\#4240](https://github.com/raster-foundry/raster-foundry/pull/4240)
- Change homepage "Create a new Template" button to "Create a new Analysis" [/#4224](https://github.com/raster-foundry/raster-foundry/pull/4224)
- Projects with > 30 scenes will not show a preview on the project list page [/#4231](https://github.com/raster-foundry/raster-foundry/pull/4231)
- Upgraded scala typelevel ecosystem [\#4215](https://github.com/raster-foundry/raster-foundry/pull/4215)
- Images no longer require a non-empty list of bands when creating scenes [\#4241](https://github.com/raster-foundry/raster-foundry/pull/4241)
- Switched to semi-automatic json codec derivation for query parameters [\#4267](https://github.com/raster-foundry/raster-foundry/pull/4267)
- Added recalculation and update of project extent after scene deletion [\#4283](https://github.com/raster-foundry/raster-foundry/pull/4283)

### Fixed
- Increase nginx buffer size & count for Scene, Tool, and Thumbnail requests [\#4170](https://github.com/raster-foundry/raster-foundry/pull/4170)
- Add user button no longer shows for non-admins of teams and orgs [\#4212](https://github.com/raster-foundry/raster-foundry/pull/4212)
- Fix undefined function call when selecting project scenes by clicking the map in advanced color correction view [\#4212](https://github.com/raster-foundry/raster-foundry/pull/4212)
- Fix visualization of Planet scenes and fix bands used when generating COG scene thumbnails [\#4238](https://github.com/raster-foundry/raster-foundry/pull/4238), [\#4262](https://github.com/raster-foundry/raster-foundry/pull/4262)
- Stopped explicitly setting a nodata value in one step of ingest for Sentinel-2 and Landsat [\#4324](https://github.com/raster-foundry/raster-foundry/pull/4234)
- Stopped combining Landsat 4 / 5 / 7 bands in random orders when converting them to COGs and added command to fix existing Landsat 4 / 5 / 7 scenes [\#4242](https://github.com/raster-foundry/raster-foundry/pull/4242), [\#4261](https://github.com/raster-foundry/raster-foundry/pull/4261)
- Cleaned up a project database test [\#4248](https://github.com/raster-foundry/raster-foundry/pull/4248)
- Don't include name in intercom user init if it's the same as the email [\#4247](https://github.com/raster-foundry/raster-foundry/pull/4247)
- Kick off ingests for scenes without scene types also [\#4260](https://github.com/raster-foundry/raster-foundry/pull/4260)
- Separated connection and transaction execution contexts in database tests [\#4264](https://github.com/raster-foundry/raster-foundry/pull/4264)
- More carefully managed system resources to prevent non-terminating asynchronous workflows [\#4268](https://github.com/raster-foundry/raster-foundry/pull/4268)
- Made search feature more secure for endpoints that supply such query parameter [\#4280](https://github.com/raster-foundry/raster-foundry/pull/4280)
- Fixed annotation click and edit bug when the annotation is uploaded from a zipped shapefile [\#4282](https://github.com/raster-foundry/raster-foundry/pull/4282)

## [1.13.0](https://github.com/raster-foundry/raster-foundry/tree/1.13.0) (2018-10-10)

### Added
- Disable blog feed and intercom initialization using webpack override file [\#4162](https://github.com/raster-foundry/raster-foundry/pull/4162)
- Add support for google tag manager via webpack overrides [\#4165](https://github.com/raster-foundry/raster-foundry/pull/4165)
- Added support for additional/future Planet asset types [\#4184](https://github.com/raster-foundry/raster-foundry/pull/4184)

### Changed

- Switched to [keepachangelog](https://keepachangelog.com/en/1.0.0/) CHANGELOG format [\#4159](https://github.com/raster-foundry/raster-foundry/pull/4159)
- Used production-hardened existing color correction for backsplash COGs instead of hand-rolled ad hoc color correction [\#4160](https://github.com/raster-foundry/raster-foundry/pull/4160)
- Restricted sharing with everyone and platforms to superusers and platform admins [\#4166](https://github.com/raster-foundry/raster-foundry/pull/4166)
- Added sbt configuration for auto-scalafmt [\#4175](https://github.com/raster-foundry/raster-foundry/pull/4175)
- Displayed user information on template items when not created by the platform [\#4172](https://github.com/raster-foundry/raster-foundry/pull/4172)
- Simplified authorization logic in backsplash [\#4176](https://github.com/raster-foundry/raster-foundry/pull/4176)
- Added a global cache location for sharing artifacts across CI builds [\#4181](https://github.com/raster-foundry/raster-foundry/pull/4181), [\#4183](https://github.com/raster-foundry/raster-foundry/pull/4183), [\#4186](https://github.com/raster-foundry/raster-foundry/pull/4186)
- Switched to using `sbt` to resolve dependencies [\#4191](https://github.com/raster-foundry/raster-foundry/pull/4191)
- Users who are not admins of an organization can now correctly create teams and are automatically added to them [\#4147](https://github.com/raster-foundry/raster-foundry/pull/4171)
- Added additional options for starting development server [\#4192](https://github.com/raster-foundry/raster-foundry/pull/4192)

### Fixed

- Removed duplicate emails for repeated failures of the same upload [\#4130](https://github.com/raster-foundry/raster-foundry/pull/4130)
- Used safer options for large tifs when processing uploads and ingests [\#4131](https://github.com/raster-foundry/raster-foundry/pull/4131)
- Re-enabled datasource deletion and disable it if there is permission attached [\#4140](https://github.com/raster-foundry/raster-foundry/pull/4140), [\#4158](https://github.com/raster-foundry/raster-foundry/pull/4158)
- Fixed permission modal bug so that it won't hang after deleting permissions [\#4174](https://github.com/raster-foundry/raster-foundry/pull/4174)
- Fixed issue with clamping imagery whose range was greater than, but included values between 0 and 255 [\#4177](https://github.com/raster-foundry/raster-foundry/pull/4177)
- Included missing `pow` operation for decoding json representations of analyses [\#4179](https://github.com/raster-foundry/raster-foundry/pull/4140), [\#4155](https://github.com/raster-foundry/raster-foundry/issues/4155)

## [1.12.0](https://github.com/raster-foundry/raster-foundry/tree/1.12.0) (2018-10-03)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/1.11.0...1.12.0)

**Merged pull requests:**

- Add visibility check to shared ownership type [\#4144](https://github.com/raster-foundry/raster-foundry/pull/4144)
- Change webpack merge strategy to override HtmlWebpackPlugin [\#4139](https://github.com/raster-foundry/raster-foundry/pull/4139)
- Fix raster data count for pagination [\#4137](https://github.com/raster-foundry/raster-foundry/pull/4137)
- Fix navbar alignment on smaller screens [\#4133](https://github.com/raster-foundry/raster-foundry/pull/4133)
- Filter imports by ownership [\#4129](https://github.com/raster-foundry/raster-foundry/pull/4129)
- Merge page sorting parameters [\#4122](https://github.com/raster-foundry/raster-foundry/pull/4122)
- Fix project export [\#4120](https://github.com/raster-foundry/raster-foundry/pull/4120)
- Staging frontend bug fix: scene date UTC filter; User profile; AOI datasource filter [\#4119](https://github.com/raster-foundry/raster-foundry/pull/4119)
- Fix annotation editing [\#4118](https://github.com/raster-foundry/raster-foundry/pull/4118)
- Fix dropbox dummy session store [\#4112](https://github.com/raster-foundry/raster-foundry/pull/4112)
- Crop over-zoomed avro tiles [\#4109](https://github.com/raster-foundry/raster-foundry/pull/4109)
- Filter out pending scenes from mosaic definitions [\#4108](https://github.com/raster-foundry/raster-foundry/pull/4108)
- Don't kick off cog ingest [\#4107](https://github.com/raster-foundry/raster-foundry/pull/4107)
- Allow removing users from an organization [\#4106](https://github.com/raster-foundry/raster-foundry/pull/4106)
- Update timestamp string parsing [\#4105](https://github.com/raster-foundry/raster-foundry/pull/4105)
- Fix scene re-ordering again [\#4104](https://github.com/raster-foundry/raster-foundry/pull/4104)
- Reset lab state when a new analysis is fetched [\#4103](https://github.com/raster-foundry/raster-foundry/pull/4103)
- Require first and last name for sharing, scrub email [\#4097](https://github.com/raster-foundry/raster-foundry/pull/4097)
- Light Theme [\#4091](https://github.com/raster-foundry/raster-foundry/pull/4091)
- Add single band category transparency rendering [\#4085](https://github.com/raster-foundry/raster-foundry/pull/4085)
- ACR in text array [\#3967](https://github.com/raster-foundry/raster-foundry/pull/3967)

## [1.11.0](https://github.com/raster-foundry/raster-foundry/tree/1.11.0) (2018-09-25)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/1.10.0...1.11.0)

**Merged pull requests:**

- Add backsplash generated files to app-migrations .dockerignore [\#4094](https://github.com/raster-foundry/raster-foundry/pull/4094)
- Add database query logging [\#4083](https://github.com/raster-foundry/raster-foundry/pull/4083)
- Add error message for failed s3 and cog uploads [\#4082](https://github.com/raster-foundry/raster-foundry/pull/4082)
- Fix misnamed function call in project list controller [\#4081](https://github.com/raster-foundry/raster-foundry/pull/4081)
- Paginate and fix AOI scene approval [\#4078](https://github.com/raster-foundry/raster-foundry/pull/4078)
- Staging frontend fix: project list, AOI datasource filter, links [\#4076](https://github.com/raster-foundry/raster-foundry/pull/4076)
- Fix band selection for singleband mosaics [\#4075](https://github.com/raster-foundry/raster-foundry/pull/4075)
- Only send email on third batch attempt or successful export [\#4054](https://github.com/raster-foundry/raster-foundry/pull/4054)
- Remove default argument from page with ordering method [\#4049](https://github.com/raster-foundry/raster-foundry/pull/4049)
- Add cron task to clean up Coursier cache [\#4048](https://github.com/raster-foundry/raster-foundry/pull/4048)
- Fix users/me endpoint [\#4047](https://github.com/raster-foundry/raster-foundry/pull/4047)
- Use COG image if available for scene download [\#4044](https://github.com/raster-foundry/raster-foundry/pull/4044)
- Route lab tile requests through single band mosaic [\#4041](https://github.com/raster-foundry/raster-foundry/pull/4041)
- Add download button to scene list [\#4035](https://github.com/raster-foundry/raster-foundry/pull/4035)
- Use AutoHigherResolution [\#4033](https://github.com/raster-foundry/raster-foundry/pull/4033)
- Add more statuses  for ingest control flow [\#4032](https://github.com/raster-foundry/raster-foundry/pull/4032)
- Add scalafmt helper script [\#4030](https://github.com/raster-foundry/raster-foundry/pull/4030)
- Allow user to search scenes using UTC or local time on frontend [\#4028](https://github.com/raster-foundry/raster-foundry/pull/4028)
- Paginate and streamline project scene views [\#4014](https://github.com/raster-foundry/raster-foundry/pull/4014)
- Add tab completion to rf cli [\#4012](https://github.com/raster-foundry/raster-foundry/pull/4012)
- Remove auth0 configuration that isn't used [\#4011](https://github.com/raster-foundry/raster-foundry/pull/4011)
- Make pan-to new annotations optional [\#4007](https://github.com/raster-foundry/raster-foundry/pull/4007)
- Filter actual list of AOI scenes to approve instead of response object [\#4006](https://github.com/raster-foundry/raster-foundry/pull/4006)
- Remove Scapegoat errors for data model project [\#3988](https://github.com/raster-foundry/raster-foundry/pull/3988)
- Add support for scalafmt via set plugin [\#3959](https://github.com/raster-foundry/raster-foundry/pull/3959)
- Remove Scapegoat errors for database project [\#3949](https://github.com/raster-foundry/raster-foundry/pull/3949)
- Use logger for ZOOMANDEXTENTS message [\#3935](https://github.com/raster-foundry/raster-foundry/pull/3935)
- \[Anchor\] Backsplash [\#3850](https://github.com/raster-foundry/raster-foundry/pull/3850)
- \[Front End\] Cleans up front-end map controls [\#3985](https://github.com/raster-foundry/raster-foundry/pull/3985)

## [1.10.0](https://github.com/raster-foundry/raster-foundry/tree/1.10.0) (2018-08-31)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/1.9.0...1.10.0)

**Merged pull requests:**

- Fix path to project in success email [\#4001](https://github.com/raster-foundry/raster-foundry/pull/4001)
- Polish COG creation for MODIS [\#4000](https://github.com/raster-foundry/raster-foundry/pull/4000)
- Fix a couple missing double-quotes on email links [\#3999](https://github.com/raster-foundry/raster-foundry/pull/3999)
- Fix multiband COG creation for Sentinel-2 and Landsat [\#3992](https://github.com/raster-foundry/raster-foundry/pull/3992)
- Fix export status update [\#3987](https://github.com/raster-foundry/raster-foundry/pull/3987)
- Fix scene item deletion frontend bug [\#3984](https://github.com/raster-foundry/raster-foundry/pull/3984)
- Simplify output processing options for project export frontend [\#3982](https://github.com/raster-foundry/raster-foundry/pull/3982)
- Refactor import notification copy [\#3980](https://github.com/raster-foundry/raster-foundry/pull/3980)
- Make planet uploads COGs [\#3979](https://github.com/raster-foundry/raster-foundry/pull/3979)
- Simplify adding scenes to projects UI [\#3955](https://github.com/raster-foundry/raster-foundry/pull/3955)
- Choose AutoHigherResolution for OverviewStrategy [\#3952](https://github.com/raster-foundry/raster-foundry/pull/3952)
- Replace deprecated \(a |@| b\) with \(a, b\).mapN [\#3947](https://github.com/raster-foundry/raster-foundry/pull/3947)
- Remove Scapegoat errors for common project [\#3946](https://github.com/raster-foundry/raster-foundry/pull/3946)
- Add option to pause AOIs [\#3943](https://github.com/raster-foundry/raster-foundry/pull/3943)
- Add postgres implicit imports back to source [\#3939](https://github.com/raster-foundry/raster-foundry/pull/3939)
- Fix non-ASCII characters in health check cache key [\#3934](https://github.com/raster-foundry/raster-foundry/pull/3934)
- Restore original page when logging in [\#3931](https://github.com/raster-foundry/raster-foundry/pull/3931)
- Fix scene double ingest bug [\#3930](https://github.com/raster-foundry/raster-foundry/pull/3930)
- Show imports in progress [\#3926](https://github.com/raster-foundry/raster-foundry/pull/3926)
- Replace ingest with cog creation for Landsat 8 and Sentinel-2 [\#3925](https://github.com/raster-foundry/raster-foundry/pull/3925)
- Don't link external s3 resources [\#3917](https://github.com/raster-foundry/raster-foundry/pull/3917)
- Remove Scapegoat errors for batch project [\#3907](https://github.com/raster-foundry/raster-foundry/pull/3907)
- Set uploadStatus to COMPLETE after a successful COG upload [\#3897](https://github.com/raster-foundry/raster-foundry/pull/3897)
- Remove Scapegoat errors for authentication project [\#3893](https://github.com/raster-foundry/raster-foundry/pull/3893)
- Remove Scapegoat errors for API project [\#3891](https://github.com/raster-foundry/raster-foundry/pull/3891)
- Fix Exports and S3 import [\#3890](https://github.com/raster-foundry/raster-foundry/pull/3890)
- Add an ADR for object-level ACRs in text arrays [\#3887](https://github.com/raster-foundry/raster-foundry/pull/3887)
- Fix lab export modal and dropbox check; Fix permission modal and tab [\#3884](https://github.com/raster-foundry/raster-foundry/pull/3884)
- Update Lodash and node-sass dependencies [\#3883](https://github.com/raster-foundry/raster-foundry/pull/3883)
- Link to status page upon successful raster upload [\#3882](https://github.com/raster-foundry/raster-foundry/pull/3882)
- Enhance and fix datasources list view [\#3879](https://github.com/raster-foundry/raster-foundry/pull/3879)
- Handle case where polygon filter returns no scenes for mosaic [\#3874](https://github.com/raster-foundry/raster-foundry/pull/3874)
- Add support for sbt-git and PGP signed build artifacts [\#3873](https://github.com/raster-foundry/raster-foundry/pull/3873)
- Fix scene item sidebar reorder bug on frontend [\#3872](https://github.com/raster-foundry/raster-foundry/pull/3872)
- Fix copy button overflow in firefox [\#3871](https://github.com/raster-foundry/raster-foundry/pull/3871)
- Add note about support for EPSG:4326 shapefiles only [\#3869](https://github.com/raster-foundry/raster-foundry/pull/3869)
- Fix reversed scene order for projects [\#3860](https://github.com/raster-foundry/raster-foundry/pull/3860)
- Add Rollbar access token to environment for infra [\#3858](https://github.com/raster-foundry/raster-foundry/pull/3858)
- Allow defining band in lab if datasource has none [\#3856](https://github.com/raster-foundry/raster-foundry/pull/3856)
- Remove option emptiness check for early stream exit [\#3855](https://github.com/raster-foundry/raster-foundry/pull/3855)
- Remove broken links on vector page [\#3854](https://github.com/raster-foundry/raster-foundry/pull/3854)
- Allow specifying mosaic color composites via url params [\#3853](https://github.com/raster-foundry/raster-foundry/pull/3853)
- Use coveredBy geometry filter in scene to project stream [\#3852](https://github.com/raster-foundry/raster-foundry/pull/3852)
- Add ownership filter controls to project list page [\#3851](https://github.com/raster-foundry/raster-foundry/pull/3851)
- Make COG export work [\#3848](https://github.com/raster-foundry/raster-foundry/pull/3848)
- Upgrade to GT 2.0 [\#3846](https://github.com/raster-foundry/raster-foundry/pull/3846)
- Add support for configuring Hikari; setup connections with statement\_timeout [\#3841](https://github.com/raster-foundry/raster-foundry/pull/3841)
- Persist map bounds when entering browse mode [\#3840](https://github.com/raster-foundry/raster-foundry/pull/3840)
- Make logging configuration more consistent [\#3829](https://github.com/raster-foundry/raster-foundry/pull/3829)
- Auto accept team invitation if user is in same organization [\#3823](https://github.com/raster-foundry/raster-foundry/pull/3823)
- Force re-fetching datasources after adding scenes to project [\#3804](https://github.com/raster-foundry/raster-foundry/pull/3804)
- Send emails after uploads succeed or fail [\#3789](https://github.com/raster-foundry/raster-foundry/pull/3789)
- Only mosaic scenes if they contribute to the visible layer [\#3783](https://github.com/raster-foundry/raster-foundry/pull/3783)
- Try Auto\(x\) instead of AutoHigherResolution for tile rendering and COG thumbnails [\#3969](https://github.com/raster-foundry/raster-foundry/pull/3969)

## [1.9.0](https://github.com/raster-foundry/raster-foundry/tree/1.9.0) (2018-08-13)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/1.8.1...1.9.0)

**Merged pull requests:**

- Fix lab analysis export metadata [\#3836](https://github.com/raster-foundry/raster-foundry/pull/3836)
- Revert change to COPY in Dockerfile.api [\#3835](https://github.com/raster-foundry/raster-foundry/pull/3835)
- Don't return scene images in scene list queries [\#3832](https://github.com/raster-foundry/raster-foundry/pull/3832)
- Add proxy for sentinel thumbnails to handle requester-pays [\#3831](https://github.com/raster-foundry/raster-foundry/pull/3831)
- Add fields and filters to projects and annotations [\#3813](https://github.com/raster-foundry/raster-foundry/pull/3813)
- Remove worthless access control rule creation from importers [\#3811](https://github.com/raster-foundry/raster-foundry/pull/3811)
- Fix annotation shapefile export and import [\#3808](https://github.com/raster-foundry/raster-foundry/pull/3808)
- Prevent container build failure when copying nginx/srv/dist before it exists [\#3800](https://github.com/raster-foundry/raster-foundry/pull/3800)
- Fix pagination on project scenes [\#3796](https://github.com/raster-foundry/raster-foundry/pull/3796)
- Add sbt-sonatype plugin to support publishing to Maven Central [\#3794](https://github.com/raster-foundry/raster-foundry/pull/3794)
- Upgrade Nginx to latest stable release [\#3648](https://github.com/raster-foundry/raster-foundry/pull/3648)

## [1.8.1](https://github.com/raster-foundry/raster-foundry/tree/1.8.1) (2018-08-09)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/1.8.0...1.8.1)

**Merged pull requests:**

- Revert GeoTrellis Upgrade [\#3825](https://github.com/raster-foundry/raster-foundry/pull/3825)

## [1.8.0](https://github.com/raster-foundry/raster-foundry/tree/1.8.0) (2018-08-07)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/1.7.1...1.8.0)

**Merged pull requests:**

- Enable requester pays for Sentinel-2 bucket access [\#3822](https://github.com/raster-foundry/raster-foundry/pull/3822)
- UI improvements and frontend scene query fix [\#3817](https://github.com/raster-foundry/raster-foundry/pull/3817)
- Correct scene geometry on insert [\#3816](https://github.com/raster-foundry/raster-foundry/pull/3816)
- Don't list entire scenes table in export definition creation [\#3812](https://github.com/raster-foundry/raster-foundry/pull/3812)
- Add Lab Async Exports [\#3809](https://github.com/raster-foundry/raster-foundry/pull/3809)
- Hide location search button in maps when a modal is open [\#3806](https://github.com/raster-foundry/raster-foundry/pull/3806)
- Fix select in permission modal cutting off text [\#3802](https://github.com/raster-foundry/raster-foundry/pull/3802)
- Fix dependency conflicts [\#3798](https://github.com/raster-foundry/raster-foundry/pull/3798)
- Fix preview changing when node histogram is opened [\#3790](https://github.com/raster-foundry/raster-foundry/pull/3790)
- Remove ingestSizeBytes from scenes [\#3784](https://github.com/raster-foundry/raster-foundry/pull/3784)
- Support lab analysis result async export [\#3782](https://github.com/raster-foundry/raster-foundry/pull/3782)
- Select shapes from the aoi parameters panel [\#3780](https://github.com/raster-foundry/raster-foundry/pull/3780)

## [1.7.1](https://github.com/raster-foundry/raster-foundry/tree/1.7.1) (2018-08-02)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/1.7.0...1.7.1)

**Merged pull requests:**

- Add overridable initial map position [\#3795](https://github.com/raster-foundry/raster-foundry/pull/3795)

## [1.7.0](https://github.com/raster-foundry/raster-foundry/tree/1.7.0) (2018-08-02)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/1.6.1...1.7.0)

**Merged pull requests:**

- Add index on datasource column in scenes table [\#3781](https://github.com/raster-foundry/raster-foundry/pull/3781)
- Make scenes with thin datasource postable [\#3779](https://github.com/raster-foundry/raster-foundry/pull/3779)
- fixed avatar sizing and position issues, btn-square is now square [\#3777](https://github.com/raster-foundry/raster-foundry/pull/3777)
- Add back project color clipping [\#3772](https://github.com/raster-foundry/raster-foundry/pull/3772)
- Use shapes for AOIs [\#3756](https://github.com/raster-foundry/raster-foundry/pull/3756)
- Add re-designed permissions modal [\#3755](https://github.com/raster-foundry/raster-foundry/pull/3755)
- Enable deleting datasources with related scenes and uploads [\#3734](https://github.com/raster-foundry/raster-foundry/pull/3734)
- Allow public project share page to be viewed by non-logged in users [\#3669](https://github.com/raster-foundry/raster-foundry/pull/3669)

## [1.6.1](https://github.com/raster-foundry/raster-foundry/tree/1.6.1) (2018-07-27)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/1.6.0...1.6.1)

**Merged pull requests:**

- Minor Admin Fixes [\#3770](https://github.com/raster-foundry/raster-foundry/pull/3770)
- Support sending notifications to user contact email [\#3752](https://github.com/raster-foundry/raster-foundry/pull/3752)

## [1.6.0](https://github.com/raster-foundry/raster-foundry/tree/1.6.0) (2018-07-26)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/1.5.0...1.6.0)

**Merged pull requests:**

- Make planet credentials less persistent in service [\#3767](https://github.com/raster-foundry/raster-foundry/pull/3767)
- Make manual date range update work for scene browse filter [\#3751](https://github.com/raster-foundry/raster-foundry/pull/3751)
- Fix Indexing Duplicates [\#3750](https://github.com/raster-foundry/raster-foundry/pull/3750)
- Refactor email notification body for org/team invitation/request [\#3746](https://github.com/raster-foundry/raster-foundry/pull/3746)
- Fix broken links and buttons on home page and datasource create modal [\#3743](https://github.com/raster-foundry/raster-foundry/pull/3743)
- Fix Export and Export Emails [\#3742](https://github.com/raster-foundry/raster-foundry/pull/3742)
- added help center and everyone sharing icons [\#3741](https://github.com/raster-foundry/raster-foundry/pull/3741)
- Allow setting role when adding a user to an organization or team [\#3739](https://github.com/raster-foundry/raster-foundry/pull/3739)
- Submit Update AOI projects from Scala [\#3738](https://github.com/raster-foundry/raster-foundry/pull/3738)
-  Add cron tasks to clean up Jenkins cache, bump versions  [\#3736](https://github.com/raster-foundry/raster-foundry/pull/3736)
- Fix Match Error in Project Mosaics [\#3735](https://github.com/raster-foundry/raster-foundry/pull/3735)
- Correct ImportLandsat8C1 and ImportSentinel2 data and tile footprint [\#3730](https://github.com/raster-foundry/raster-foundry/pull/3730)
- Fix bringing up development environment from scratch [\#3725](https://github.com/raster-foundry/raster-foundry/pull/3725)
- Add additional fields to user profile [\#3723](https://github.com/raster-foundry/raster-foundry/pull/3723)

## [1.5.0](https://github.com/raster-foundry/raster-foundry/tree/1.5.0) (2018-07-18)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/1.4.0...1.5.0)

**Merged pull requests:**

- Fixes whitebalance issues for multiple scenes in a project [\#3727](https://github.com/raster-foundry/raster-foundry/pull/3727)
- Update auth0 lock [\#3722](https://github.com/raster-foundry/raster-foundry/pull/3722)
- Add cascade deletion when it makes sense [\#3720](https://github.com/raster-foundry/raster-foundry/pull/3720)
- Remove Zoom Restriction for Browsing Scenes in UI [\#3719](https://github.com/raster-foundry/raster-foundry/pull/3719)
- Fix annotation shapefile export [\#3718](https://github.com/raster-foundry/raster-foundry/pull/3718)
- Scene Query Improvements [\#3717](https://github.com/raster-foundry/raster-foundry/pull/3717)
- Remove landsat scenes with \< 0 cloud cover [\#3715](https://github.com/raster-foundry/raster-foundry/pull/3715)
- Allow importing GeoJSON annotation with blank properties [\#3714](https://github.com/raster-foundry/raster-foundry/pull/3714)
- Only transform band wavelength if it's a string [\#3713](https://github.com/raster-foundry/raster-foundry/pull/3713)
- Fix download for MODIS scenes [\#3712](https://github.com/raster-foundry/raster-foundry/pull/3712)
- Fix async exports for ingested scenes [\#3710](https://github.com/raster-foundry/raster-foundry/pull/3710)
- Add project list preview mini leaflet maps [\#3706](https://github.com/raster-foundry/raster-foundry/pull/3706)
- Add fallthrough mosaic match to extent and zoom fetch [\#3705](https://github.com/raster-foundry/raster-foundry/pull/3705)
- Fix sentinel 2 ingest definition generation to fix missing band [\#3703](https://github.com/raster-foundry/raster-foundry/pull/3703)
- Fix browse COG scene tile bug [\#3693](https://github.com/raster-foundry/raster-foundry/pull/3693)
- Update redirect\_uris [\#3691](https://github.com/raster-foundry/raster-foundry/pull/3691)
- Fix MODIS thumbnails [\#3686](https://github.com/raster-foundry/raster-foundry/pull/3686)
- Allow saving of bands without wavelength in UI [\#3684](https://github.com/raster-foundry/raster-foundry/pull/3684)
- Fix organization setting button bug [\#3676](https://github.com/raster-foundry/raster-foundry/pull/3676)
- Use BUILDCONFIG.APP\_NAME as base repository label [\#3674](https://github.com/raster-foundry/raster-foundry/pull/3674)
- Add team search backend support [\#3661](https://github.com/raster-foundry/raster-foundry/pull/3661)
- Fixup authorization in team and organization routes [\#3654](https://github.com/raster-foundry/raster-foundry/pull/3654)
- Use correct cloud coverage field for Sentinel-2 [\#3643](https://github.com/raster-foundry/raster-foundry/pull/3643)
- Normalize pagination and add to all group object lists [\#3641](https://github.com/raster-foundry/raster-foundry/pull/3641)

## [1.4.0](https://github.com/raster-foundry/raster-foundry/tree/1.4.0) (2018-07-09)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/1.3.0...1.4.0)

**Merged pull requests:**

- Simplify and fix conditional logic in onRepositoryChange [\#3655](https://github.com/raster-foundry/raster-foundry/pull/3655)
- Fix scene download permission bug [\#3653](https://github.com/raster-foundry/raster-foundry/pull/3653)
- Support COG tile in scene detail view. [\#3649](https://github.com/raster-foundry/raster-foundry/pull/3649)
- Enable Single Band Visualization for COGs [\#3647](https://github.com/raster-foundry/raster-foundry/pull/3647)
- Only search for scenes if zoom level \>= 8 when in RF repository [\#3646](https://github.com/raster-foundry/raster-foundry/pull/3646)
- Add COG scene browse support for thumbnail and tiles [\#3640](https://github.com/raster-foundry/raster-foundry/pull/3640)
- Allow updating wavelength from datasource detail view [\#3639](https://github.com/raster-foundry/raster-foundry/pull/3639)
- Remove Tri-Decadal MSS from NASA CMR search options [\#3638](https://github.com/raster-foundry/raster-foundry/pull/3638)
- Search for users and organizations in the navbar [\#3635](https://github.com/raster-foundry/raster-foundry/pull/3635)
- Add some enhancements on admin and user frontend [\#3633](https://github.com/raster-foundry/raster-foundry/pull/3633)
- Polish local COG upload processing [\#3631](https://github.com/raster-foundry/raster-foundry/pull/3631)
- Add default sort to platform member list [\#3630](https://github.com/raster-foundry/raster-foundry/pull/3630)
- Fix AOI update and approval bug [\#3628](https://github.com/raster-foundry/raster-foundry/pull/3628)
- Allow separating annotations into groups [\#3627](https://github.com/raster-foundry/raster-foundry/pull/3627)
- Generate thumbnails from COG scenes [\#3626](https://github.com/raster-foundry/raster-foundry/pull/3626)
- Fix add planet token bug [\#3624](https://github.com/raster-foundry/raster-foundry/pull/3624)
- Fix empty exported annotation shapefile bug [\#3619](https://github.com/raster-foundry/raster-foundry/pull/3619)
- Add and use color map modal [\#3613](https://github.com/raster-foundry/raster-foundry/pull/3613)

## [1.3.0](https://github.com/raster-foundry/raster-foundry/tree/1.3.0) (2018-06-29)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/1.2.1...1.3.0)

**Merged pull requests:**

- Fix redirect to token page [\#3625](https://github.com/raster-foundry/raster-foundry/pull/3625)
- Create cogs in upload processing [\#3618](https://github.com/raster-foundry/raster-foundry/pull/3618)
- cleaned up project card frontend and project title hover bug [\#3616](https://github.com/raster-foundry/raster-foundry/pull/3616)
- Make org de/activation work. Act on membership request/invitations. [\#3604](https://github.com/raster-foundry/raster-foundry/pull/3604)
- Use authViewQuery for AOI scenes [\#3602](https://github.com/raster-foundry/raster-foundry/pull/3602)
- Undo swagger spec deployment from \#1402 [\#3600](https://github.com/raster-foundry/raster-foundry/pull/3600)
- Support shapefile annotation upload [\#3599](https://github.com/raster-foundry/raster-foundry/pull/3599)
- Prevent multiple active roles on the same group from being created [\#3592](https://github.com/raster-foundry/raster-foundry/pull/3592)
- Remove active organization requirement to log in [\#3591](https://github.com/raster-foundry/raster-foundry/pull/3591)
- Export and AOI notifications [\#3586](https://github.com/raster-foundry/raster-foundry/pull/3586)

## [1.2.1](https://github.com/raster-foundry/raster-foundry/tree/1.2.1) (2018-06-26)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/1.2.0...1.2.1)

**Merged pull requests:**

- Differentiate rendered tiles from raw tiles for caching [\#3596](https://github.com/raster-foundry/raster-foundry/pull/3596)

## [1.2.0](https://github.com/raster-foundry/raster-foundry/tree/1.2.0) (2018-06-25)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/1.1.1...1.2.0)

**Merged pull requests:**

- Distinct on ids when counting [\#3590](https://github.com/raster-foundry/raster-foundry/pull/3590)
- Support Organization Creation Requests [\#3585](https://github.com/raster-foundry/raster-foundry/pull/3585)
- Fix ingest notification bug [\#3584](https://github.com/raster-foundry/raster-foundry/pull/3584)
- Send emails when people request to join groups or are invited [\#3582](https://github.com/raster-foundry/raster-foundry/pull/3582)
- Add User/Org/Team Profiles + Refresh UI [\#3579](https://github.com/raster-foundry/raster-foundry/pull/3579)
- Update platform email settings and ingest email notification [\#3578](https://github.com/raster-foundry/raster-foundry/pull/3578)
- Fix anti-meridian migration [\#3576](https://github.com/raster-foundry/raster-foundry/pull/3576)
- Add user search endpoint [\#3575](https://github.com/raster-foundry/raster-foundry/pull/3575)
- Fix Planet Upload Processing [\#3574](https://github.com/raster-foundry/raster-foundry/pull/3574)
- Move email to common subproject [\#3572](https://github.com/raster-foundry/raster-foundry/pull/3572)
- Update top-level objects' listing filters [\#3571](https://github.com/raster-foundry/raster-foundry/pull/3571)
- Add org search endpoint [\#3569](https://github.com/raster-foundry/raster-foundry/pull/3569)
- Separate Staging and production deployment pipelines [\#3568](https://github.com/raster-foundry/raster-foundry/pull/3568)
- Add user group role acceptance statuses [\#3567](https://github.com/raster-foundry/raster-foundry/pull/3567)
- Tell users where to update profile photo [\#3566](https://github.com/raster-foundry/raster-foundry/pull/3566)
- Anti-meridian search fixes, migration for fixing data\_footprints [\#3563](https://github.com/raster-foundry/raster-foundry/pull/3563)
- Display ownership information with projects [\#3549](https://github.com/raster-foundry/raster-foundry/pull/3549)
- Filter organizations by visibility and platform [\#3531](https://github.com/raster-foundry/raster-foundry/pull/3531)

## [1.1.1](https://github.com/raster-foundry/raster-foundry/tree/1.1.1) (2018-06-19)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/1.1.0...1.1.1)

**Merged pull requests:**

- Update scene authorization check to use updated logic [\#3562](https://github.com/raster-foundry/raster-foundry/pull/3562)
- Fix add user to team bug [\#3554](https://github.com/raster-foundry/raster-foundry/pull/3554)
- Make organization and team names editable for admins [\#3550](https://github.com/raster-foundry/raster-foundry/pull/3550)
- More frontend fixes [\#3539](https://github.com/raster-foundry/raster-foundry/pull/3539)
- Make sending ingest notification emails work [\#3525](https://github.com/raster-foundry/raster-foundry/pull/3525)

## [1.1.0](https://github.com/raster-foundry/raster-foundry/tree/1.1.0) (2018-06-18)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/1.0.3...1.1.0)

**Merged pull requests:**

- Use rasterio warp module to warp modis tifs [\#3548](https://github.com/raster-foundry/raster-foundry/pull/3548)
- Improve Scene Query Performance [\#3547](https://github.com/raster-foundry/raster-foundry/pull/3547)
- Add support for lat long coordinates in map search [\#3541](https://github.com/raster-foundry/raster-foundry/pull/3541)
- Add frontend support for Landsat 4, 5, and 7 [\#3540](https://github.com/raster-foundry/raster-foundry/pull/3540)
- Add processing for Landsat 4, 5, and 7 scenes [\#3529](https://github.com/raster-foundry/raster-foundry/pull/3529)

## [1.0.3](https://github.com/raster-foundry/raster-foundry/tree/1.0.3) (2018-06-14)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/1.0.2...1.0.3)

**Merged pull requests:**

- Restrict Projects to Owners for Frontend [\#3542](https://github.com/raster-foundry/raster-foundry/pull/3542)
- Signed requests should never send a HEAD request [\#3516](https://github.com/raster-foundry/raster-foundry/pull/3516)
- Allow organization admins to add and modify roles for that organization [\#3511](https://github.com/raster-foundry/raster-foundry/pull/3511)
- Platform email notification settings frontend [\#3501](https://github.com/raster-foundry/raster-foundry/pull/3501)
-  Add RF\_DEPLOYMENT\_ENVIRONMENT variable [\#3500](https://github.com/raster-foundry/raster-foundry/pull/3500)

## [1.0.2](https://github.com/raster-foundry/raster-foundry/tree/1.0.2) (2018-06-12)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/1.0.1...1.0.2)

**Merged pull requests:**

- Refactor csv processing strategy [\#3502](https://github.com/raster-foundry/raster-foundry/pull/3502)
- Fix anti-meridian artifacts on scene footprints [\#3496](https://github.com/raster-foundry/raster-foundry/pull/3496)
- Show placeholder when no org logo present [\#3495](https://github.com/raster-foundry/raster-foundry/pull/3495)
- Replace copied with scene [\#3494](https://github.com/raster-foundry/raster-foundry/pull/3494)
- Show emails only when user proves some commonality with others [\#3493](https://github.com/raster-foundry/raster-foundry/pull/3493)
- Add quiet option to scripts/server [\#3491](https://github.com/raster-foundry/raster-foundry/pull/3491)
- Various front-end cleanup items [\#3490](https://github.com/raster-foundry/raster-foundry/pull/3490)
- Add Platform Email Settings Storage Backend [\#3489](https://github.com/raster-foundry/raster-foundry/pull/3489)
- add landsat 4 5 7 product datasources [\#3459](https://github.com/raster-foundry/raster-foundry/pull/3459)

## [1.0.1](https://github.com/raster-foundry/raster-foundry/tree/1.0.1) (2018-06-08)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/1.0.0...1.0.1)

## [1.0.0](https://github.com/raster-foundry/raster-foundry/tree/1.0.0) (2018-06-08)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.36.0...1.0.0)

**Merged pull requests:**

- Bump up MAML version [\#3481](https://github.com/raster-foundry/raster-foundry/pull/3481)
- Get subject name in sharing modal. [\#3477](https://github.com/raster-foundry/raster-foundry/pull/3477)
- Make tool permissions from tools instead of datasources [\#3476](https://github.com/raster-foundry/raster-foundry/pull/3476)
- Handle case where user tries to log in without a platform or an org [\#3475](https://github.com/raster-foundry/raster-foundry/pull/3475)
- Fix creating EVERYONE permissions [\#3474](https://github.com/raster-foundry/raster-foundry/pull/3474)
- Various Auth/Ingest Fixes [\#3473](https://github.com/raster-foundry/raster-foundry/pull/3473)
- Place all users in the RF platform [\#3470](https://github.com/raster-foundry/raster-foundry/pull/3470)
- Apply visual design to admin views [\#3468](https://github.com/raster-foundry/raster-foundry/pull/3468)
- Add COG/MODIS upload handling [\#3467](https://github.com/raster-foundry/raster-foundry/pull/3467)
- Frontend support for admin adding org logo [\#3463](https://github.com/raster-foundry/raster-foundry/pull/3463)
- Move authorization query to inner join [\#3462](https://github.com/raster-foundry/raster-foundry/pull/3462)
- Add first cut permissions modal [\#3458](https://github.com/raster-foundry/raster-foundry/pull/3458)
- Eagerly insert scenes and flip csv date order [\#3454](https://github.com/raster-foundry/raster-foundry/pull/3454)
- Fix platform admin user search [\#3452](https://github.com/raster-foundry/raster-foundry/pull/3452)
- Update admin UI permission and failure messages  [\#3450](https://github.com/raster-foundry/raster-foundry/pull/3450)
- Fix text in the organization add user modal [\#3449](https://github.com/raster-foundry/raster-foundry/pull/3449)
- Endpoints for listing user-specific actions on objects [\#3448](https://github.com/raster-foundry/raster-foundry/pull/3448)
- Add organization and platform enabling/disabling endpoints [\#3447](https://github.com/raster-foundry/raster-foundry/pull/3447)
- Enable admins posting org logo and storing it on S3 [\#3446](https://github.com/raster-foundry/raster-foundry/pull/3446)
- Parameterize app name on raster and datasource pages. [\#3440](https://github.com/raster-foundry/raster-foundry/pull/3440)
- Add third party credentials to organizations [\#3439](https://github.com/raster-foundry/raster-foundry/pull/3439)
- Create AccessControlRules on public scene import [\#3438](https://github.com/raster-foundry/raster-foundry/pull/3438)
- Get more datasource information from scenes [\#3437](https://github.com/raster-foundry/raster-foundry/pull/3437)
- Add permission routes to remaining first class auth objects [\#3432](https://github.com/raster-foundry/raster-foundry/pull/3432)
- Add access control rules for public objects [\#3427](https://github.com/raster-foundry/raster-foundry/pull/3427)
- Add secondary data model authorization [\#3426](https://github.com/raster-foundry/raster-foundry/pull/3426)
- Set max classfile name length [\#3425](https://github.com/raster-foundry/raster-foundry/pull/3425)
- Allow deactivating teams [\#3424](https://github.com/raster-foundry/raster-foundry/pull/3424)
- Allow platform admins to change a user's organization [\#3413](https://github.com/raster-foundry/raster-foundry/pull/3413)
- Add platforms and organizations management and refactor authorization [\#3130](https://github.com/raster-foundry/raster-foundry/pull/3130)

## [0.36.0](https://github.com/raster-foundry/raster-foundry/tree/0.36.0) (2018-05-23)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.35.0...0.36.0)

**Merged pull requests:**

- Fix AOI list filter and other aoi bugs [\#3418](https://github.com/raster-foundry/raster-foundry/pull/3418)
- Better parallelize sentinel2 import [\#3415](https://github.com/raster-foundry/raster-foundry/pull/3415)
- Respect time and space filters to find scenes to update aoi projects [\#3412](https://github.com/raster-foundry/raster-foundry/pull/3412)
- Catch geometry parsing errors when reading Sentinel 2 scenes [\#3404](https://github.com/raster-foundry/raster-foundry/pull/3404)
- Update .gitignore to handle .envrc files [\#3401](https://github.com/raster-foundry/raster-foundry/pull/3401)
- Remove errant debugging argument for get\_tempdir [\#3400](https://github.com/raster-foundry/raster-foundry/pull/3400)
- Add MODIS Scene Creation from Browsing [\#3397](https://github.com/raster-foundry/raster-foundry/pull/3397)
- Update STRTA: `load\_development\_data` and `fix-migration` [\#3396](https://github.com/raster-foundry/raster-foundry/pull/3396)
- Allow adding MODIS scenes to projects [\#3393](https://github.com/raster-foundry/raster-foundry/pull/3393)
- Add requirement for DAO tests [\#3362](https://github.com/raster-foundry/raster-foundry/pull/3362)

## [0.35.0](https://github.com/raster-foundry/raster-foundry/tree/0.35.0) (2018-05-16)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.34.2...0.35.0)

**Merged pull requests:**

- Support COG-based Projects [\#3349](https://github.com/raster-foundry/raster-foundry/pull/3349)

## [0.34.2](https://github.com/raster-foundry/raster-foundry/tree/0.34.2) (2018-05-09)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.34.1...0.34.2)

**Merged pull requests:**

- Use parent scope's project when opening scene import modal [\#3347](https://github.com/raster-foundry/raster-foundry/pull/3347)
- Add Landsat 4, 5, and 7 gcs path construction util [\#3342](https://github.com/raster-foundry/raster-foundry/pull/3342)
- Use Sid repositories for installing GDAL [\#3341](https://github.com/raster-foundry/raster-foundry/pull/3341)
- Handle shape filters separately from other scene query params [\#3340](https://github.com/raster-foundry/raster-foundry/pull/3340)
- Disable Failed Retries for Ingests from API [\#3338](https://github.com/raster-foundry/raster-foundry/pull/3338)
- Add redirect to a connection specified in BUILDCONFIG [\#3332](https://github.com/raster-foundry/raster-foundry/pull/3332)
- Add scene type to scenes [\#3322](https://github.com/raster-foundry/raster-foundry/pull/3322)
- Remove PRs from CHANGELOG not targeting release branch [\#3320](https://github.com/raster-foundry/raster-foundry/pull/3320)
- Use AWS batch job attempt to fail early after retries [\#3290](https://github.com/raster-foundry/raster-foundry/pull/3290)

## [0.34.1](https://github.com/raster-foundry/raster-foundry/tree/0.34.1) (2018-05-01)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.34.0...0.34.1)

## [0.34.0](https://github.com/raster-foundry/raster-foundry/tree/0.34.0) (2018-05-01)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.33.3...0.34.0)

**Merged pull requests:**

- Use S3 client instead of hadoop writer for S3 exports [\#3301](https://github.com/raster-foundry/raster-foundry/pull/3301)
- Add isActive to AOIs [\#3300](https://github.com/raster-foundry/raster-foundry/pull/3300)
- Update export definition on an export when we create it [\#3295](https://github.com/raster-foundry/raster-foundry/pull/3295)
- Install python-requests via apt to fix broken pip install [\#3289](https://github.com/raster-foundry/raster-foundry/pull/3289)
- Use correct image and thumbnail source uris [\#3279](https://github.com/raster-foundry/raster-foundry/pull/3279)
- Fix scene visibility filtering [\#3275](https://github.com/raster-foundry/raster-foundry/pull/3275)
- Fix sql and bash cmd in update-aoi-projects [\#3272](https://github.com/raster-foundry/raster-foundry/pull/3272)
- Add migration for MODIS datasources/products [\#3270](https://github.com/raster-foundry/raster-foundry/pull/3270)
- Fix excessive scene queries from hasNext [\#3269](https://github.com/raster-foundry/raster-foundry/pull/3269)
- Use Spark Local for Exports [\#3259](https://github.com/raster-foundry/raster-foundry/pull/3259)
- Extend upload type enum in datamodel and database [\#3255](https://github.com/raster-foundry/raster-foundry/pull/3255)
- Coordinate doobie output from find aoi projects and cli input [\#3254](https://github.com/raster-foundry/raster-foundry/pull/3254)
- Fix sql queries and stop actor system in Sentinel 2 import [\#3253](https://github.com/raster-foundry/raster-foundry/pull/3253)
- Restore Landsat 8 to Landsat 8 C1 import redirection [\#3242](https://github.com/raster-foundry/raster-foundry/pull/3242)
- Make unreachable scene filtering code reachable [\#3241](https://github.com/raster-foundry/raster-foundry/pull/3241)
- Return and display approximate scene counts for many scenes [\#3237](https://github.com/raster-foundry/raster-foundry/pull/3237)
- Attempt to resolve login issues [\#3236](https://github.com/raster-foundry/raster-foundry/pull/3236)
- Kickoff ingests after adding scenes to projects everywhere [\#3234](https://github.com/raster-foundry/raster-foundry/pull/3234)
- Separately run mg init and ignore errors [\#3231](https://github.com/raster-foundry/raster-foundry/pull/3231)
- Use Rollbar Java SDK for exception reporting [\#3228](https://github.com/raster-foundry/raster-foundry/pull/3228)
- Fix Hanging postgres metadata write [\#3227](https://github.com/raster-foundry/raster-foundry/pull/3227)
- Fix scene downloads endpoint [\#3222](https://github.com/raster-foundry/raster-foundry/pull/3222)
- Save multiple drawn polygons as MultiPolygon for shape filter [\#3221](https://github.com/raster-foundry/raster-foundry/pull/3221)
- Fix credential empty string handling [\#3220](https://github.com/raster-foundry/raster-foundry/pull/3220)
- Let scene detail modal persist when date picker modal is opened [\#3219](https://github.com/raster-foundry/raster-foundry/pull/3219)
- Fix Dao page compilation and datasource filtering [\#3203](https://github.com/raster-foundry/raster-foundry/pull/3203)
- Attempt to suppress Scapegoat OOM errors [\#3201](https://github.com/raster-foundry/raster-foundry/pull/3201)
- Fix page ordering when `DESC` [\#3200](https://github.com/raster-foundry/raster-foundry/pull/3200)
- Use OpenJDK 8 JDK for Spark tasks [\#3198](https://github.com/raster-foundry/raster-foundry/pull/3198)
- Fix accidental angular module conflict - ProfileController [\#3196](https://github.com/raster-foundry/raster-foundry/pull/3196)
- Fix listing of project AOIs [\#3193](https://github.com/raster-foundry/raster-foundry/pull/3193)
- Fix user filter - Don't default to organizational sharing [\#3191](https://github.com/raster-foundry/raster-foundry/pull/3191)
- Rework scenes sort and index [\#3190](https://github.com/raster-foundry/raster-foundry/pull/3190)
- Update `annotation-reducer.js` to handle GeoJSON [\#3186](https://github.com/raster-foundry/raster-foundry/pull/3186)
- Delete datasource after updating scenes [\#3177](https://github.com/raster-foundry/raster-foundry/pull/3177)
- Add checklist item to PR template for APP\_NAME templating [\#3173](https://github.com/raster-foundry/raster-foundry/pull/3173)
- Enable search by name/title/description on project/datasource/tool list endpoints [\#3153](https://github.com/raster-foundry/raster-foundry/pull/3153)
- Annotation export as shapefile and project annotation api bug fix [\#3144](https://github.com/raster-foundry/raster-foundry/pull/3144)
- Add shape filter to scenes in api and hook up frontend [\#3142](https://github.com/raster-foundry/raster-foundry/pull/3142)
- \[Anchor\] Add property tests for Dao interactions [\#3134](https://github.com/raster-foundry/raster-foundry/pull/3134)
- Update pull request template [\#3126](https://github.com/raster-foundry/raster-foundry/pull/3126)
- Progress toward working async workflows [\#3124](https://github.com/raster-foundry/raster-foundry/pull/3124)
- Get all paginated datasources upon scene browse [\#3114](https://github.com/raster-foundry/raster-foundry/pull/3114)
- Treat Sentinel-2A/B as the same datasource in batch import and in DB [\#3113](https://github.com/raster-foundry/raster-foundry/pull/3113)
- Upgrade aws-cli version [\#3110](https://github.com/raster-foundry/raster-foundry/pull/3110)
- Fix hasNext calculations for scenes [\#3107](https://github.com/raster-foundry/raster-foundry/pull/3107)
- Add scaffeine local cache to tile-server to improve cache performance [\#3096](https://github.com/raster-foundry/raster-foundry/pull/3096)
- Disable white-balance correction [\#3088](https://github.com/raster-foundry/raster-foundry/pull/3088)
- Improve scene list query so it doesn't crash the database [\#3086](https://github.com/raster-foundry/raster-foundry/pull/3086)
- Add help center to show customer specific videos & docs [\#3083](https://github.com/raster-foundry/raster-foundry/pull/3083)
- Restore unintentionally removed migration config [\#3082](https://github.com/raster-foundry/raster-foundry/pull/3082)
- Fix database and migration settings [\#3081](https://github.com/raster-foundry/raster-foundry/pull/3081)
- Revert unintentional changes to Vagrantfile and db settings [\#3079](https://github.com/raster-foundry/raster-foundry/pull/3079)
- Fix migration link location and migration content [\#3078](https://github.com/raster-foundry/raster-foundry/pull/3078)
- Enable copy confirmation on sharing buttons [\#3063](https://github.com/raster-foundry/raster-foundry/pull/3063)
- Lab workspace ADR [\#3061](https://github.com/raster-foundry/raster-foundry/pull/3061)
- Add band count validation to local file upload modal [\#3028](https://github.com/raster-foundry/raster-foundry/pull/3028)
- Add frontend support for datasource licenses [\#2998](https://github.com/raster-foundry/raster-foundry/pull/2998)
- Convert database interaction from slick to doobie [\#2989](https://github.com/raster-foundry/raster-foundry/pull/2989)
- Trigger deployment stages of build for branches prefixed with /test [\#2949](https://github.com/raster-foundry/raster-foundry/pull/2949)

## [0.33.3](https://github.com/raster-foundry/raster-foundry/tree/0.33.3) (2018-03-19)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.33.2...0.33.3)

**Merged pull requests:**

- Fix colormodes [\#3075](https://github.com/raster-foundry/raster-foundry/pull/3075)
- Fix broken logout button [\#3074](https://github.com/raster-foundry/raster-foundry/pull/3074)

## [0.33.2](https://github.com/raster-foundry/raster-foundry/tree/0.33.2) (2018-03-16)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.33.1...0.33.2)

**Merged pull requests:**

- Change acquisition date display to UTC [\#3060](https://github.com/raster-foundry/raster-foundry/pull/3060)
- Fix scene delete and scene display after being added issues [\#3057](https://github.com/raster-foundry/raster-foundry/pull/3057)

## [0.33.1](https://github.com/raster-foundry/raster-foundry/tree/0.33.1) (2018-03-13)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.33.0...0.33.1)

**Merged pull requests:**

- Fix share page bug. [\#3051](https://github.com/raster-foundry/raster-foundry/pull/3051)

## [0.33.0](https://github.com/raster-foundry/raster-foundry/tree/0.33.0) (2018-03-13)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.32.0...0.33.0)

**Merged pull requests:**

- Use BUILDCONFIG logo on share page [\#3050](https://github.com/raster-foundry/raster-foundry/pull/3050)
- Fix broken planet thumbnails. [\#3046](https://github.com/raster-foundry/raster-foundry/pull/3046)
- Fix node ordering in lab [\#3030](https://github.com/raster-foundry/raster-foundry/pull/3030)
- Preview scenes by clicking the thumbnail, select by clicking [\#3026](https://github.com/raster-foundry/raster-foundry/pull/3026)
- Enable scene re-ordering in the UI [\#3010](https://github.com/raster-foundry/raster-foundry/pull/3010)
- Add platform routes and skeleton [\#3005](https://github.com/raster-foundry/raster-foundry/pull/3005)

## [0.32.0](https://github.com/raster-foundry/raster-foundry/tree/0.32.0) (2018-03-06)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.31.0...0.32.0)

**Merged pull requests:**

- Add swagger spec for raw analyses render endpoint [\#3003](https://github.com/raster-foundry/raster-foundry/pull/3003)
- Fix dropdown filter styling [\#3001](https://github.com/raster-foundry/raster-foundry/pull/3001)
- Annotation shapefile export [\#3000](https://github.com/raster-foundry/raster-foundry/pull/3000)
- Overzooming for Multiband Export [\#2997](https://github.com/raster-foundry/raster-foundry/pull/2997)
- Double planet thumbnail resolution [\#2995](https://github.com/raster-foundry/raster-foundry/pull/2995)
- Set a default raster size for export [\#2993](https://github.com/raster-foundry/raster-foundry/pull/2993)
- Resolve dependency NPE error [\#2991](https://github.com/raster-foundry/raster-foundry/pull/2991)
- Regriding and Logic Fix for GeoTiff Exporting [\#2988](https://github.com/raster-foundry/raster-foundry/pull/2988)
- fixed several browser issues for both themes [\#2987](https://github.com/raster-foundry/raster-foundry/pull/2987)
- Docker cron-task should run daily [\#2986](https://github.com/raster-foundry/raster-foundry/pull/2986)
- Create a JSON interface for datasource licenses. [\#2984](https://github.com/raster-foundry/raster-foundry/pull/2984)
- Extend upload credential timeout to an hour [\#2983](https://github.com/raster-foundry/raster-foundry/pull/2983)
- Create admin routes, with user management and team tabs roughed out [\#2982](https://github.com/raster-foundry/raster-foundry/pull/2982)
- Seed licenses data to the licenses table [\#2979](https://github.com/raster-foundry/raster-foundry/pull/2979)
- Use built-in Docker subcommands for cleanup [\#2978](https://github.com/raster-foundry/raster-foundry/pull/2978)
- Improve image overlay clipping [\#2977](https://github.com/raster-foundry/raster-foundry/pull/2977)
- Update geotiff tile-footprint generation to handle multiple NODATA areas [\#2976](https://github.com/raster-foundry/raster-foundry/pull/2976)
- Create modals for adding users, teams, and organizations [\#2974](https://github.com/raster-foundry/raster-foundry/pull/2974)
- Allow changing datasource in scene detail modal [\#2973](https://github.com/raster-foundry/raster-foundry/pull/2973)
- Create a licenses table and a license column in datasources table [\#2968](https://github.com/raster-foundry/raster-foundry/pull/2968)
- Fix scene modal [\#2960](https://github.com/raster-foundry/raster-foundry/pull/2960)
- \[WIP\] ADR - Permissions and Architecture for Organizations [\#2958](https://github.com/raster-foundry/raster-foundry/pull/2958)
- Add shape browse filter [\#2953](https://github.com/raster-foundry/raster-foundry/pull/2953)
- Indicate Scene Permissions on Results in Browser [\#2952](https://github.com/raster-foundry/raster-foundry/pull/2952)
- Bump cats ecosystem to the 1.0 release [\#2948](https://github.com/raster-foundry/raster-foundry/pull/2948)
- Add browsing of select CMR collections [\#2947](https://github.com/raster-foundry/raster-foundry/pull/2947)
- Explicitly Order resolvers for Dependencies in `build.sbt` [\#2945](https://github.com/raster-foundry/raster-foundry/pull/2945)
- Feature/lk/fix planet thumbnail scaling [\#2941](https://github.com/raster-foundry/raster-foundry/pull/2941)
- Add shared/consistent sorting for scene ordering in projects [\#2939](https://github.com/raster-foundry/raster-foundry/pull/2939)
- Add Vector view and upload modal [\#2937](https://github.com/raster-foundry/raster-foundry/pull/2937)
- UI revisions for the project list page [\#2935](https://github.com/raster-foundry/raster-foundry/pull/2935)
- Adjust logout timer [\#2934](https://github.com/raster-foundry/raster-foundry/pull/2934)
- styling fix for high-contrast theme datasources filter dropdown [\#2929](https://github.com/raster-foundry/raster-foundry/pull/2929)
- Update jquery [\#2926](https://github.com/raster-foundry/raster-foundry/pull/2926)
- Add notification stub to project AOI updates [\#2925](https://github.com/raster-foundry/raster-foundry/pull/2925)
- Finish supporting Planet browsing [\#2921](https://github.com/raster-foundry/raster-foundry/pull/2921)
- Add Shapefile Upload Processing Route for Shapes API [\#2919](https://github.com/raster-foundry/raster-foundry/pull/2919)

## [0.31.0](https://github.com/raster-foundry/raster-foundry/tree/0.31.0) (2018-01-19)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.30.0...0.31.0)

**Merged pull requests:**

- Determine annotation organization from user [\#2916](https://github.com/raster-foundry/raster-foundry/pull/2916)
- Datasource band support [\#2909](https://github.com/raster-foundry/raster-foundry/pull/2909)
- Set constant column width for analysis modified date and deletion [\#2884](https://github.com/raster-foundry/raster-foundry/pull/2884)

## [0.30.0](https://github.com/raster-foundry/raster-foundry/tree/0.30.0) (2018-01-18)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.29.0...0.30.0)

**Merged pull requests:**

- Fix Development Caching [\#2913](https://github.com/raster-foundry/raster-foundry/pull/2913)
- Styles project export's output sidebar for high-contrast theme [\#2906](https://github.com/raster-foundry/raster-foundry/pull/2906)
- Use `ui-select` component for datasource filter on browse page [\#2905](https://github.com/raster-foundry/raster-foundry/pull/2905)
- Add band editing to datasource detail UI [\#2903](https://github.com/raster-foundry/raster-foundry/pull/2903)
- Remove unique constraint on tools \(org + title\) [\#2902](https://github.com/raster-foundry/raster-foundry/pull/2902)
- Use `API\_HOST` in all API url construction [\#2900](https://github.com/raster-foundry/raster-foundry/pull/2900)
- Handle edge cases when uploading annotations better [\#2895](https://github.com/raster-foundry/raster-foundry/pull/2895)
- Re-index datasource color composites starting at 0 [\#2882](https://github.com/raster-foundry/raster-foundry/pull/2882)
- Add unit conversions button to lab measurements [\#2881](https://github.com/raster-foundry/raster-foundry/pull/2881)
- Hide node histogram breakpoints until they are hovered [\#2880](https://github.com/raster-foundry/raster-foundry/pull/2880)
- Use gdal for footprint extraction [\#2879](https://github.com/raster-foundry/raster-foundry/pull/2879)
- Use `API\_HOST` for project export url [\#2878](https://github.com/raster-foundry/raster-foundry/pull/2878)
- Fixed a layout bug in high-contract theme introduced with \#2852 [\#2876](https://github.com/raster-foundry/raster-foundry/pull/2876)
- Add additional datasources [\#2875](https://github.com/raster-foundry/raster-foundry/pull/2875)
- Improves ux and design for imports and datasource pages [\#2873](https://github.com/raster-foundry/raster-foundry/pull/2873)
- Add a "raw" endpoint to render analyses as single band tifs [\#2867](https://github.com/raster-foundry/raster-foundry/pull/2867)
- Use GeoTrellis 1.2.0 [\#2734](https://github.com/raster-foundry/raster-foundry/pull/2734)

## [0.29.0](https://github.com/raster-foundry/raster-foundry/tree/0.29.0) (2018-01-04)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.28.0...0.29.0)

**Merged pull requests:**

- Add feature flags for browsing external imagery sources [\#2866](https://github.com/raster-foundry/raster-foundry/pull/2866)
- Cache datasource get requests [\#2864](https://github.com/raster-foundry/raster-foundry/pull/2864)
- Fix sort order being indeterminate when objects have the same value [\#2861](https://github.com/raster-foundry/raster-foundry/pull/2861)
- Use combination of 2 Docker Compose configurations for CI [\#2860](https://github.com/raster-foundry/raster-foundry/pull/2860)
- Adjust styles to handle custom checkbox icons [\#2859](https://github.com/raster-foundry/raster-foundry/pull/2859)
- Clean up gradient dropdown style [\#2858](https://github.com/raster-foundry/raster-foundry/pull/2858)
- Allow renaming of nodes in the lab [\#2856](https://github.com/raster-foundry/raster-foundry/pull/2856)
- Fixes style issues with imagery sources dropdown [\#2854](https://github.com/raster-foundry/raster-foundry/pull/2854)
- Remove 'trash' buttons from Scenes list [\#2853](https://github.com/raster-foundry/raster-foundry/pull/2853)
- Fix project list when project name is long [\#2852](https://github.com/raster-foundry/raster-foundry/pull/2852)
- Implement Shapes API [\#2851](https://github.com/raster-foundry/raster-foundry/pull/2851)
- Read stac feature from geojson and output scene [\#2850](https://github.com/raster-foundry/raster-foundry/pull/2850)
- Consistently use upload completion method [\#2848](https://github.com/raster-foundry/raster-foundry/pull/2848)
- Download support for uploads [\#2832](https://github.com/raster-foundry/raster-foundry/pull/2832)
- Allow map drag when drawing polygonal annotations w/o adding new nodes [\#2831](https://github.com/raster-foundry/raster-foundry/pull/2831)
- Remove race condition for ingest queuing [\#2830](https://github.com/raster-foundry/raster-foundry/pull/2830)
- Remove unused sentinel2 upload processing [\#2828](https://github.com/raster-foundry/raster-foundry/pull/2828)
- Check for all sentinel datasource ids in ingest process [\#2826](https://github.com/raster-foundry/raster-foundry/pull/2826)
- Planet import fix, checkbox update for icomoon [\#2825](https://github.com/raster-foundry/raster-foundry/pull/2825)
- Add new fix for kicking off ingests when new scenes added to project [\#2822](https://github.com/raster-foundry/raster-foundry/pull/2822)
- Add notification step to ingest process with stubbed notifications [\#2811](https://github.com/raster-foundry/raster-foundry/pull/2811)
- Add spark local ingest option to `rf` [\#2806](https://github.com/raster-foundry/raster-foundry/pull/2806)
- Add another log during ingestion process [\#2804](https://github.com/raster-foundry/raster-foundry/pull/2804)
- Create stac datamodels [\#2803](https://github.com/raster-foundry/raster-foundry/pull/2803)
- Use redux actions for lab map and allow deletion of analyses [\#2802](https://github.com/raster-foundry/raster-foundry/pull/2802)
- Bug fix - can't draw rectangular annotation after single click on map [\#2789](https://github.com/raster-foundry/raster-foundry/pull/2789)
- Clean up planet filters and browsing [\#2782](https://github.com/raster-foundry/raster-foundry/pull/2782)
- Enable RF/Planet data switch, planet data filter, and planet data browse [\#2675](https://github.com/raster-foundry/raster-foundry/pull/2675)

## [0.28.0](https://github.com/raster-foundry/raster-foundry/tree/0.28.0) (2017-12-11)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.27.0...0.28.0)

**Merged pull requests:**

- Workaround for map init timing [\#2800](https://github.com/raster-foundry/raster-foundry/pull/2800)
- Use ingest status filter when constructing mosaics [\#2799](https://github.com/raster-foundry/raster-foundry/pull/2799)
- Moved our icon font from fontello to icomoon and added missing icons [\#2798](https://github.com/raster-foundry/raster-foundry/pull/2798)
- Explicitly set tile-server log level to `INFO` [\#2796](https://github.com/raster-foundry/raster-foundry/pull/2796)
- Add param to generate empty grid layer [\#2795](https://github.com/raster-foundry/raster-foundry/pull/2795)
- Modify importer to separate Sentinel-2A and 2B scenes [\#2793](https://github.com/raster-foundry/raster-foundry/pull/2793)
- Migration to split Sentinel-2A and Sentinel-2B datasources [\#2787](https://github.com/raster-foundry/raster-foundry/pull/2787)

## [0.27.0](https://github.com/raster-foundry/raster-foundry/tree/0.27.0) (2017-12-07)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.26.0...0.27.0)

**Merged pull requests:**

- Fix support for handling refresh tokens [\#2783](https://github.com/raster-foundry/raster-foundry/pull/2783)
- Re-add lab node share [\#2781](https://github.com/raster-foundry/raster-foundry/pull/2781)
- Fix caching to avoid cached NODATA tile errors [\#2777](https://github.com/raster-foundry/raster-foundry/pull/2777)
- Fix squished login header [\#2776](https://github.com/raster-foundry/raster-foundry/pull/2776)
- Stub out batch jar cli for reading Stac asset [\#2774](https://github.com/raster-foundry/raster-foundry/pull/2774)
- Log errors when batch submitJob fails [\#2772](https://github.com/raster-foundry/raster-foundry/pull/2772)
- Add text to indicate drag and drop upload [\#2770](https://github.com/raster-foundry/raster-foundry/pull/2770)
- Show histogram and stats on inputs [\#2769](https://github.com/raster-foundry/raster-foundry/pull/2769)
- Rename tool -\> template, tool-run -\> analysis [\#2768](https://github.com/raster-foundry/raster-foundry/pull/2768)
- Store analysis sort order in local storage [\#2765](https://github.com/raster-foundry/raster-foundry/pull/2765)
- Use annotations API on the frontend [\#2763](https://github.com/raster-foundry/raster-foundry/pull/2763)
- Create modal service to centralize modal state [\#2762](https://github.com/raster-foundry/raster-foundry/pull/2762)

## [0.26.0](https://github.com/raster-foundry/raster-foundry/tree/0.26.0) (2017-12-01)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.25.0...0.26.0)

**Merged pull requests:**

- Add sorting to tool-runs [\#2758](https://github.com/raster-foundry/raster-foundry/pull/2758)
- Fix export file listing [\#2748](https://github.com/raster-foundry/raster-foundry/pull/2748)
- Fix `getBaseUrl` in authService to use current location [\#2747](https://github.com/raster-foundry/raster-foundry/pull/2747)
- Fix tile-server non-color-corrected exports [\#2746](https://github.com/raster-foundry/raster-foundry/pull/2746)
- Remove Auth0 client secret configuration from repository [\#2745](https://github.com/raster-foundry/raster-foundry/pull/2745)
- Explicitly use options in cache client [\#2744](https://github.com/raster-foundry/raster-foundry/pull/2744)
- Use batch jar path environment variable for export jobs [\#2742](https://github.com/raster-foundry/raster-foundry/pull/2742)
- Add Auth0 Login Theme Support [\#2737](https://github.com/raster-foundry/raster-foundry/pull/2737)
- Extract token from header instead of parameter [\#2736](https://github.com/raster-foundry/raster-foundry/pull/2736)
- Fix setting geojson layer not overwriting [\#2730](https://github.com/raster-foundry/raster-foundry/pull/2730)
- Fix exports clipped using a polygon [\#2728](https://github.com/raster-foundry/raster-foundry/pull/2728)
- Fix s3 exports [\#2726](https://github.com/raster-foundry/raster-foundry/pull/2726)
- Use STS to retrieve S3 credentials [\#2725](https://github.com/raster-foundry/raster-foundry/pull/2725)
- Add `HELPCONFIG` to allow configuring help links [\#2722](https://github.com/raster-foundry/raster-foundry/pull/2722)
- Fix tab styling in tokens view [\#2719](https://github.com/raster-foundry/raster-foundry/pull/2719)
- Add migration to delete duplicate unused landsat scenes [\#2718](https://github.com/raster-foundry/raster-foundry/pull/2718)
- Restrict tool run query to owners [\#2717](https://github.com/raster-foundry/raster-foundry/pull/2717)
- Fix homepage links, scene preview modal init [\#2715](https://github.com/raster-foundry/raster-foundry/pull/2715)
- Adjust histogram / breakpoints behavior [\#2711](https://github.com/raster-foundry/raster-foundry/pull/2711)
- Update spec [\#2704](https://github.com/raster-foundry/raster-foundry/pull/2704)
- Update RF logo and logo styles [\#2702](https://github.com/raster-foundry/raster-foundry/pull/2702)
- Add lab action toolbar with preview and compare buttons [\#2701](https://github.com/raster-foundry/raster-foundry/pull/2701)
- Add source parameter to feed endpoint [\#2700](https://github.com/raster-foundry/raster-foundry/pull/2700)
- Add click to preview imports [\#2699](https://github.com/raster-foundry/raster-foundry/pull/2699)
- Implement Annotation API [\#2694](https://github.com/raster-foundry/raster-foundry/pull/2694)
- Fix project share urls using the api url instead of frontend url [\#2693](https://github.com/raster-foundry/raster-foundry/pull/2693)
- Manage state in lab using Redux [\#2690](https://github.com/raster-foundry/raster-foundry/pull/2690)
- Configure Ingest jobs to use their own batch queue [\#2686](https://github.com/raster-foundry/raster-foundry/pull/2686)
- Add result stats component, fix glitch [\#2683](https://github.com/raster-foundry/raster-foundry/pull/2683)
- Refactor Sharing view to use standard markup [\#2681](https://github.com/raster-foundry/raster-foundry/pull/2681)
- Fix migration 85 [\#2676](https://github.com/raster-foundry/raster-foundry/pull/2676)
- Fix node stats number alignment, decimal places, and commas [\#2674](https://github.com/raster-foundry/raster-foundry/pull/2674)
- Annotation API Spec [\#2673](https://github.com/raster-foundry/raster-foundry/pull/2673)
- Fix project export - don't assume a tool is being used [\#2672](https://github.com/raster-foundry/raster-foundry/pull/2672)
- WIP: radiant theme [\#2671](https://github.com/raster-foundry/raster-foundry/pull/2671)
- Redirect requests for /tiles to tile service root [\#2670](https://github.com/raster-foundry/raster-foundry/pull/2670)
- Migration to add natural color mode to sent2 imagery [\#2668](https://github.com/raster-foundry/raster-foundry/pull/2668)
- Fix colormode overwriting color corrections [\#2667](https://github.com/raster-foundry/raster-foundry/pull/2667)
- Add field to users for notification opt-in status [\#2666](https://github.com/raster-foundry/raster-foundry/pull/2666)
- Initialize Color Correction Values to Sane Defaults [\#2665](https://github.com/raster-foundry/raster-foundry/pull/2665)
- Fix AOI filter's data source, reset, close button, styling issues [\#2664](https://github.com/raster-foundry/raster-foundry/pull/2664)
- Display 'Untitled tool' on tool page instead of blank [\#2663](https://github.com/raster-foundry/raster-foundry/pull/2663)
- Move to OIDC compliant endpoint on POST /api/tokens [\#2657](https://github.com/raster-foundry/raster-foundry/pull/2657)
- Create new label name autocomplete for annotations [\#2656](https://github.com/raster-foundry/raster-foundry/pull/2656)
- Serve tiles from service root instead of /tiles [\#2655](https://github.com/raster-foundry/raster-foundry/pull/2655)
- UI fixes for project list view, bulk annotations, and lab panning [\#2649](https://github.com/raster-foundry/raster-foundry/pull/2649)
- Use context managed temp directory to clean up tempfiles for Sentinel Ingest [\#2648](https://github.com/raster-foundry/raster-foundry/pull/2648)
- Reproject sentinel 2 tifs to Web Mercator before ingest [\#2647](https://github.com/raster-foundry/raster-foundry/pull/2647)
- Add retry and backoff to ingest statuses [\#2645](https://github.com/raster-foundry/raster-foundry/pull/2645)
- Add names to tool runs, refactor tools ui for future work [\#2643](https://github.com/raster-foundry/raster-foundry/pull/2643)
- Reduce build requirements for migrations container [\#2639](https://github.com/raster-foundry/raster-foundry/pull/2639)
- Shorten RfmlTileResolver [\#2638](https://github.com/raster-foundry/raster-foundry/pull/2638)
- Fix tool sorting [\#2636](https://github.com/raster-foundry/raster-foundry/pull/2636)
- Fix template file reference for lint [\#2634](https://github.com/raster-foundry/raster-foundry/pull/2634)
- Upload split files to platform bucket rather than source bucket [\#2633](https://github.com/raster-foundry/raster-foundry/pull/2633)
- Sort scenes by acquisition date if available [\#2632](https://github.com/raster-foundry/raster-foundry/pull/2632)
- Create tool-run listing and search page [\#2631](https://github.com/raster-foundry/raster-foundry/pull/2631)
- Optimize thumbnails on map [\#2630](https://github.com/raster-foundry/raster-foundry/pull/2630)
- Use correct branch for CI deployment [\#2626](https://github.com/raster-foundry/raster-foundry/pull/2626)
- MAML integration [\#2595](https://github.com/raster-foundry/raster-foundry/pull/2595)

## [0.25.0](https://github.com/raster-foundry/raster-foundry/tree/0.25.0) (2017-10-08)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.24.0...0.25.0)

**Merged pull requests:**

- Add rollbar support for cache errors [\#2625](https://github.com/raster-foundry/raster-foundry/pull/2625)
- Fix comparing lab nodes [\#2623](https://github.com/raster-foundry/raster-foundry/pull/2623)
- Fix annotate draw handler existence issue [\#2622](https://github.com/raster-foundry/raster-foundry/pull/2622)
- Fix node histogram color schemes [\#2621](https://github.com/raster-foundry/raster-foundry/pull/2621)
- Bulk annotation mode and interrupt fix [\#2620](https://github.com/raster-foundry/raster-foundry/pull/2620)
- Fix date selection button bug on scene filter pane. [\#2619](https://github.com/raster-foundry/raster-foundry/pull/2619)
- Remove ability to apply auto color corrections [\#2618](https://github.com/raster-foundry/raster-foundry/pull/2618)
- Move `search` component to single-file component style [\#2615](https://github.com/raster-foundry/raster-foundry/pull/2615)
- Misc project and lab usability fixes [\#2613](https://github.com/raster-foundry/raster-foundry/pull/2613)

## [0.24.0](https://github.com/raster-foundry/raster-foundry/tree/0.24.0) (2017-10-05)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.23.0...0.24.0)

**Merged pull requests:**

- Add search to project list page and project select modal [\#2614](https://github.com/raster-foundry/raster-foundry/pull/2614)
- Remove feature flag for tools [\#2605](https://github.com/raster-foundry/raster-foundry/pull/2605)
- Lab UI - Implement reclassify modal histogram [\#2579](https://github.com/raster-foundry/raster-foundry/pull/2579)

## [0.23.0](https://github.com/raster-foundry/raster-foundry/tree/0.23.0) (2017-10-05)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.22.0...0.23.0)

**Merged pull requests:**

- Reduce Database Load during Sentinel 2 Import [\#2600](https://github.com/raster-foundry/raster-foundry/pull/2600)
- Add tool share modal [\#2599](https://github.com/raster-foundry/raster-foundry/pull/2599)
- Use deployment repository master branch for production [\#2598](https://github.com/raster-foundry/raster-foundry/pull/2598)
- Allow specifying color ramps using percentage breakpoints [\#2596](https://github.com/raster-foundry/raster-foundry/pull/2596)
- Make Sentinel Import More Robust [\#2594](https://github.com/raster-foundry/raster-foundry/pull/2594)
- Lab UI - Fix node ordering [\#2589](https://github.com/raster-foundry/raster-foundry/pull/2589)
- Add bands jsonb property to datasources [\#2584](https://github.com/raster-foundry/raster-foundry/pull/2584)
- Add button to reverse lab node color schemes [\#2583](https://github.com/raster-foundry/raster-foundry/pull/2583)
- Add measurement tool to lab map [\#2582](https://github.com/raster-foundry/raster-foundry/pull/2582)
- Color scheme dropdown qualitative support [\#2573](https://github.com/raster-foundry/raster-foundry/pull/2573)
- Use project tiles to preview node if node is a source [\#2572](https://github.com/raster-foundry/raster-foundry/pull/2572)
- Allow editing uploaded scene's properties and metadata. [\#2545](https://github.com/raster-foundry/raster-foundry/pull/2545)

## [0.22.0](https://github.com/raster-foundry/raster-foundry/tree/0.22.0) (2017-09-27)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.21.0...0.22.0)

**Merged pull requests:**

- Install imagemagick in batch container [\#2574](https://github.com/raster-foundry/raster-foundry/pull/2574)
- Replace check\_database with docker-compose healthcheck [\#2551](https://github.com/raster-foundry/raster-foundry/pull/2551)
- Full AST on toolrun [\#2509](https://github.com/raster-foundry/raster-foundry/pull/2509)

## [0.21.0](https://github.com/raster-foundry/raster-foundry/tree/0.21.0) (2017-09-25)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.20.2...0.21.0)

**Merged pull requests:**

- Replace old HERE codes with up-to-date ones [\#2563](https://github.com/raster-foundry/raster-foundry/pull/2563)
- Ensure export command is visible in `rf` command line tool [\#2562](https://github.com/raster-foundry/raster-foundry/pull/2562)
- Filter scene grid endpoint on user visibility [\#2561](https://github.com/raster-foundry/raster-foundry/pull/2561)
- Use debian base for raster-foundry-batch image [\#2560](https://github.com/raster-foundry/raster-foundry/pull/2560)
- Minor Frontend Fixups after Design Refactor [\#2559](https://github.com/raster-foundry/raster-foundry/pull/2559)
- Remove All References to Airflow [\#2558](https://github.com/raster-foundry/raster-foundry/pull/2558)
- Fix typo in Boto3 S3 delete call [\#2556](https://github.com/raster-foundry/raster-foundry/pull/2556)
- Fix join of filtered scenes to paged scenes [\#2552](https://github.com/raster-foundry/raster-foundry/pull/2552)
- Add method to enable displaying returned stats in tool node UI [\#2550](https://github.com/raster-foundry/raster-foundry/pull/2550)
- Replace Airflow with AWS Batch Calls in API Server [\#2549](https://github.com/raster-foundry/raster-foundry/pull/2549)
- Check database and memcached reachability in tile server healthcheck [\#2547](https://github.com/raster-foundry/raster-foundry/pull/2547)
- Enable ToolRun Statistics [\#2544](https://github.com/raster-foundry/raster-foundry/pull/2544)
- Add '\_dn' analytic types for Planet import [\#2541](https://github.com/raster-foundry/raster-foundry/pull/2541)
- Run airflow container as root instead of airflow [\#2536](https://github.com/raster-foundry/raster-foundry/pull/2536)
- Scale color map to histogram on first preview in lab [\#2535](https://github.com/raster-foundry/raster-foundry/pull/2535)
- Limit Uploads API responses to the requesting user [\#2534](https://github.com/raster-foundry/raster-foundry/pull/2534)
- ADR-00019: Replace Airflow with AWS Batch [\#2532](https://github.com/raster-foundry/raster-foundry/pull/2532)
- WIP: cleans up large portions of the projects interface [\#2531](https://github.com/raster-foundry/raster-foundry/pull/2531)
- Update project environment variable template [\#2530](https://github.com/raster-foundry/raster-foundry/pull/2530)
- Hide non-endpoint breakpoints for continuous node color schemes [\#2528](https://github.com/raster-foundry/raster-foundry/pull/2528)
- Rename Batch environment variables for AoI commands [\#2525](https://github.com/raster-foundry/raster-foundry/pull/2525)
- Rename jobUpdateAoiProject parameter to projectId [\#2523](https://github.com/raster-foundry/raster-foundry/pull/2523)
- Use color scheme dropdown selector for single band projects [\#2522](https://github.com/raster-foundry/raster-foundry/pull/2522)
- Implement statistics node display in lab UI [\#2520](https://github.com/raster-foundry/raster-foundry/pull/2520)
- Use mapDouble in interpolation logic [\#2519](https://github.com/raster-foundry/raster-foundry/pull/2519)
- Fix share page zoom levels [\#2517](https://github.com/raster-foundry/raster-foundry/pull/2517)
- Use acquisitionDate instead of createdAt if available on scene [\#2516](https://github.com/raster-foundry/raster-foundry/pull/2516)
- Add color scheme dropdown to lab histograms [\#2513](https://github.com/raster-foundry/raster-foundry/pull/2513)
- Add ingest handling for Sentinel-2 Jpeg2000 images [\#2511](https://github.com/raster-foundry/raster-foundry/pull/2511)
- Add `rf` command line client to process asynchronous tasks [\#2505](https://github.com/raster-foundry/raster-foundry/pull/2505)
- Enable pagination and searching for Tools [\#2504](https://github.com/raster-foundry/raster-foundry/pull/2504)
- Do not show uningested scene thumbnails in project edit by default [\#2503](https://github.com/raster-foundry/raster-foundry/pull/2503)
- Change basemap image coordinates [\#2499](https://github.com/raster-foundry/raster-foundry/pull/2499)
- Set maxZoom at map level [\#2497](https://github.com/raster-foundry/raster-foundry/pull/2497)
- Add Packer to Jenkins deployment configuration [\#2496](https://github.com/raster-foundry/raster-foundry/pull/2496)
- Fix batch job exception handling [\#2494](https://github.com/raster-foundry/raster-foundry/pull/2494)
- Remove installation of third-party vagrant-rsync plugin [\#2493](https://github.com/raster-foundry/raster-foundry/pull/2493)
- Add modal for editing reclassification values in reclassify tool nodes [\#2484](https://github.com/raster-foundry/raster-foundry/pull/2484)
- Fix undefined datasource js error in filter pane [\#2482](https://github.com/raster-foundry/raster-foundry/pull/2482)
- Add lab node histograms and a default color ramp [\#2481](https://github.com/raster-foundry/raster-foundry/pull/2481)
- Mark Scene as failed if ingest definition fails [\#2478](https://github.com/raster-foundry/raster-foundry/pull/2478)
- Remove unnecessary python-dev build dependency [\#2477](https://github.com/raster-foundry/raster-foundry/pull/2477)
- Implement `classify` node in lab UI [\#2476](https://github.com/raster-foundry/raster-foundry/pull/2476)
- Support new rendering definition on the backend [\#2475](https://github.com/raster-foundry/raster-foundry/pull/2475)
- Add parsing of `classify` function in mamlscript [\#2474](https://github.com/raster-foundry/raster-foundry/pull/2474)
- Color scheme dropdown [\#2467](https://github.com/raster-foundry/raster-foundry/pull/2467)
- Staging Bug Fix - Annotation page refresh upon new shape creation in a new project [\#2461](https://github.com/raster-foundry/raster-foundry/pull/2461)
- Rebuild CHANGELOG [\#2456](https://github.com/raster-foundry/raster-foundry/pull/2456)
- Staging Bug Fix - the persistence of annotation rotate handles [\#2452](https://github.com/raster-foundry/raster-foundry/pull/2452)
- Add and update new fields annotation UI. Update import and export UI. [\#2435](https://github.com/raster-foundry/raster-foundry/pull/2435)
- \[WIP\] Use Rsync shared folders [\#2415](https://github.com/raster-foundry/raster-foundry/pull/2415)

## [0.20.2](https://github.com/raster-foundry/raster-foundry/tree/0.20.2) (2017-08-24)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.20.1...0.20.2)

**Merged pull requests:**

- Update CORS to allow requests from other domains [\#2460](https://github.com/raster-foundry/raster-foundry/pull/2460)
- Add override support for `APP\_NAME` in frontend [\#2458](https://github.com/raster-foundry/raster-foundry/pull/2458)

## [0.20.1](https://github.com/raster-foundry/raster-foundry/tree/0.20.1) (2017-08-24)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.20.0...0.20.1)

**Merged pull requests:**

- Fix password reset button; Allow Intercom APP\_ID to be specified in overrides [\#2455](https://github.com/raster-foundry/raster-foundry/pull/2455)
- Correctly handle disabled gamma and saturation options in API [\#2434](https://github.com/raster-foundry/raster-foundry/pull/2434)

## [0.20.0](https://github.com/raster-foundry/raster-foundry/tree/0.20.0) (2017-08-22)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.20.0-test-do-not-merge...0.20.0)

**Merged pull requests:**

- Don't cache None tiles forever [\#2430](https://github.com/raster-foundry/raster-foundry/pull/2430)
- Add page titles for routes to make navigation easier [\#2429](https://github.com/raster-foundry/raster-foundry/pull/2429)
- Implement new histogram breakpoints and use for min/max [\#2422](https://github.com/raster-foundry/raster-foundry/pull/2422)
- Add hotkeys for annotation-related actions. Enable shape rotate/rescale. [\#2420](https://github.com/raster-foundry/raster-foundry/pull/2420)

## [0.20.0-test-do-not-merge](https://github.com/raster-foundry/raster-foundry/tree/0.20.0-test-do-not-merge) (2017-08-22)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.19.0...0.20.0-test-do-not-merge)

**Merged pull requests:**

- Fix thumbnails on import page [\#2428](https://github.com/raster-foundry/raster-foundry/pull/2428)
- Parse bucket more delicately from upload s3 source [\#2427](https://github.com/raster-foundry/raster-foundry/pull/2427)
- Parse s3 prefixes more delicately [\#2426](https://github.com/raster-foundry/raster-foundry/pull/2426)
- Add issue template [\#2424](https://github.com/raster-foundry/raster-foundry/pull/2424)
- Update Airflow configuration file after 1.8.x upgrade [\#2419](https://github.com/raster-foundry/raster-foundry/pull/2419)
- Set catchup to false for scheduled DAGs [\#2418](https://github.com/raster-foundry/raster-foundry/pull/2418)
- Upgrade airflow to 1.8.1 and decrease boto logging verbosity [\#2413](https://github.com/raster-foundry/raster-foundry/pull/2413)
- duplicate, map-pin, polygon icons [\#2412](https://github.com/raster-foundry/raster-foundry/pull/2412)
- Implement color ramps for single band projects [\#2411](https://github.com/raster-foundry/raster-foundry/pull/2411)
- Add annotations' edit, delete, filter, clone, import, export features [\#2407](https://github.com/raster-foundry/raster-foundry/pull/2407)
- Implement custom and single band color mode config UI [\#2380](https://github.com/raster-foundry/raster-foundry/pull/2380)

## [0.19.0](https://github.com/raster-foundry/raster-foundry/tree/0.19.0) (2017-08-13)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.18.1...0.19.0)

**Merged pull requests:**

- Add Map Token support for Tool Runs [\#2409](https://github.com/raster-foundry/raster-foundry/pull/2409)
- Use provided color ramp in tool endpoint [\#2408](https://github.com/raster-foundry/raster-foundry/pull/2408)
- Jitter ingest status sleep time and kickoff ingests exactly once for uploads [\#2405](https://github.com/raster-foundry/raster-foundry/pull/2405)
- Add global statistics endpoint for tool-runs [\#2401](https://github.com/raster-foundry/raster-foundry/pull/2401)
- Remove mention-bot configuration [\#2394](https://github.com/raster-foundry/raster-foundry/pull/2394)

## [0.18.1](https://github.com/raster-foundry/raster-foundry/tree/0.18.1) (2017-08-10)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.18.0...0.18.1)

**Merged pull requests:**

- Clean Up API Spec/Queries/Planet Imports/Autocomplete [\#2378](https://github.com/raster-foundry/raster-foundry/pull/2378)
- Pass overrides from toolrun to interpreter [\#2340](https://github.com/raster-foundry/raster-foundry/pull/2340)
- Allow for AST validation w/o toolrun [\#2269](https://github.com/raster-foundry/raster-foundry/pull/2269)

## [0.18.0](https://github.com/raster-foundry/raster-foundry/tree/0.18.0) (2017-08-08)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.17.0...0.18.0)

**Merged pull requests:**

- Add frontend/import support for Planet imagery [\#2376](https://github.com/raster-foundry/raster-foundry/pull/2376)
- Allow backend handling of planet imagery imports [\#2375](https://github.com/raster-foundry/raster-foundry/pull/2375)
- Add frontend and API for setting Planet token [\#2373](https://github.com/raster-foundry/raster-foundry/pull/2373)
- Correctly implement histogram equalization in color-correction [\#2371](https://github.com/raster-foundry/raster-foundry/pull/2371)
- Add single band parameters to project model and api [\#2369](https://github.com/raster-foundry/raster-foundry/pull/2369)
- Update swagger spec for single band color correct [\#2366](https://github.com/raster-foundry/raster-foundry/pull/2366)
- Re-enable aerial basemaps [\#2364](https://github.com/raster-foundry/raster-foundry/pull/2364)
- Don't kickoff ingests twice [\#2362](https://github.com/raster-foundry/raster-foundry/pull/2362)
- Set default initial values of rgb clipping to min/max clipping [\#2359](https://github.com/raster-foundry/raster-foundry/pull/2359)
- Handle missing images when scenes' images don't exist in s3 [\#2358](https://github.com/raster-foundry/raster-foundry/pull/2358)
- Rewrite leaflet side-by-side [\#2351](https://github.com/raster-foundry/raster-foundry/pull/2351)
- Add Memcached timeout and client mode settings [\#2347](https://github.com/raster-foundry/raster-foundry/pull/2347)
- Improve bash callout when setting layer metadata in ingest [\#2345](https://github.com/raster-foundry/raster-foundry/pull/2345)
- Add vague name shame to PR template [\#2342](https://github.com/raster-foundry/raster-foundry/pull/2342)
- Add frontend parsing for many more local ops [\#2332](https://github.com/raster-foundry/raster-foundry/pull/2332)
- Add Annotations to Sidebar [\#2320](https://github.com/raster-foundry/raster-foundry/pull/2320)
- Add Annotation Toolbar Component [\#2313](https://github.com/raster-foundry/raster-foundry/pull/2313)
- Fix AOI Updates [\#2312](https://github.com/raster-foundry/raster-foundry/pull/2312)
- Include StatusCodes with createFoo routes [\#2310](https://github.com/raster-foundry/raster-foundry/pull/2310)
- Install openjdk 8 in airflow containers [\#2308](https://github.com/raster-foundry/raster-foundry/pull/2308)
- Fix map loading text location to be centered on map [\#2307](https://github.com/raster-foundry/raster-foundry/pull/2307)
- Feature/lk/organize services [\#2302](https://github.com/raster-foundry/raster-foundry/pull/2302)
- Remove resource-specific metrics [\#2301](https://github.com/raster-foundry/raster-foundry/pull/2301)
- Allow deleting scenes if scene owner or in root org [\#2300](https://github.com/raster-foundry/raster-foundry/pull/2300)
- Cache Refactor in Tile Server [\#2296](https://github.com/raster-foundry/raster-foundry/pull/2296)
- Kickoff ingests for scene creation and updating [\#2294](https://github.com/raster-foundry/raster-foundry/pull/2294)
- Add and use nodeSelector component in split map view for lab [\#2293](https://github.com/raster-foundry/raster-foundry/pull/2293)
- Send metadata when importing [\#2292](https://github.com/raster-foundry/raster-foundry/pull/2292)
- Add geocoded search to map controls [\#2290](https://github.com/raster-foundry/raster-foundry/pull/2290)
- Add a host of local operations [\#2287](https://github.com/raster-foundry/raster-foundry/pull/2287)
- Add annotation route and page for projects [\#2285](https://github.com/raster-foundry/raster-foundry/pull/2285)
- Parse acquisitionDate and cloudCover from upload metadata [\#2284](https://github.com/raster-foundry/raster-foundry/pull/2284)
- Angular Service to Query Planet API [\#2272](https://github.com/raster-foundry/raster-foundry/pull/2272)
- Change edit to view on project list and link thumbnails to edit page [\#2271](https://github.com/raster-foundry/raster-foundry/pull/2271)
- Change default basemap to light/positron [\#2270](https://github.com/raster-foundry/raster-foundry/pull/2270)
- Add JMX remote support to API and tile server [\#2266](https://github.com/raster-foundry/raster-foundry/pull/2266)
- Include scene post body params [\#2264](https://github.com/raster-foundry/raster-foundry/pull/2264)
- Add lab map component and use in lab2 [\#2263](https://github.com/raster-foundry/raster-foundry/pull/2263)
- Tune Slick/Hikari database configuration [\#2259](https://github.com/raster-foundry/raster-foundry/pull/2259)
- Fix undefined $log [\#2258](https://github.com/raster-foundry/raster-foundry/pull/2258)
- Remove extra export options [\#2252](https://github.com/raster-foundry/raster-foundry/pull/2252)
- Allow deleting already shared projects [\#2249](https://github.com/raster-foundry/raster-foundry/pull/2249)
- Feature/lk/operation node body [\#2239](https://github.com/raster-foundry/raster-foundry/pull/2239)
- Split large uploaded Tif Files [\#2231](https://github.com/raster-foundry/raster-foundry/pull/2231)
- Add ability to create tools from expressions [\#2229](https://github.com/raster-foundry/raster-foundry/pull/2229)
- Implement focal operation support [\#2228](https://github.com/raster-foundry/raster-foundry/pull/2228)
- Fix thumbnails caching blow up [\#2222](https://github.com/raster-foundry/raster-foundry/pull/2222)
- Add Node header component [\#2220](https://github.com/raster-foundry/raster-foundry/pull/2220)
- Explain directive combination ordering [\#2218](https://github.com/raster-foundry/raster-foundry/pull/2218)
- Fix sigmoidal contrast correction application [\#2217](https://github.com/raster-foundry/raster-foundry/pull/2217)
- Flag to disable memcached heap [\#2216](https://github.com/raster-foundry/raster-foundry/pull/2216)
- S3 upload process [\#2215](https://github.com/raster-foundry/raster-foundry/pull/2215)
- Feature/tnation/jenkinsfile [\#2212](https://github.com/raster-foundry/raster-foundry/pull/2212)
- Add impersonation info to Heap [\#2206](https://github.com/raster-foundry/raster-foundry/pull/2206)
- Fix possible undefined project state [\#2205](https://github.com/raster-foundry/raster-foundry/pull/2205)
- So You Wanna Extend the AST? [\#2204](https://github.com/raster-foundry/raster-foundry/pull/2204)
- Add tool refs within exports [\#2202](https://github.com/raster-foundry/raster-foundry/pull/2202)
- \[Experiment\] PostgresAttribute Store [\#2200](https://github.com/raster-foundry/raster-foundry/pull/2200)
- Improved metrics and caching [\#2198](https://github.com/raster-foundry/raster-foundry/pull/2198)
- Account for `Constant` tiles in Export AST evaluation [\#2197](https://github.com/raster-foundry/raster-foundry/pull/2197)
- Feature/lk/jointjs foreign element [\#2192](https://github.com/raster-foundry/raster-foundry/pull/2192)
- Add route for new tool run \(run2\), tool navbar [\#2188](https://github.com/raster-foundry/raster-foundry/pull/2188)

## [0.17.0](https://github.com/raster-foundry/raster-foundry/tree/0.17.0) (2017-07-05)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.16.0...0.17.0)

**Merged pull requests:**

- Add `planetCredential` field to user model [\#2196](https://github.com/raster-foundry/raster-foundry/pull/2196)
- Add redirection and throttling for downloads [\#2191](https://github.com/raster-foundry/raster-foundry/pull/2191)
- Disable running tests during assembly [\#2190](https://github.com/raster-foundry/raster-foundry/pull/2190)
- Allow Auth0 user impersonation [\#2186](https://github.com/raster-foundry/raster-foundry/pull/2186)
- Add PostgreSQL 9.6 support [\#2184](https://github.com/raster-foundry/raster-foundry/pull/2184)
- Minor Fixes/Frontend Customization for API Server [\#2181](https://github.com/raster-foundry/raster-foundry/pull/2181)
- Fix scene removal from project on project details page [\#2180](https://github.com/raster-foundry/raster-foundry/pull/2180)
- Enable changing datasource visibility on datasource detail page [\#2138](https://github.com/raster-foundry/raster-foundry/pull/2138)
- Make polygons visible after saving AOIs/loading AOI parameters page [\#2132](https://github.com/raster-foundry/raster-foundry/pull/2132)
- Add tools and enable some for export [\#2129](https://github.com/raster-foundry/raster-foundry/pull/2129)
- \[WIP\] Add Kamon metrics tracking [\#2120](https://github.com/raster-foundry/raster-foundry/pull/2120)
- Add external S3 bucket target to export UI [\#2110](https://github.com/raster-foundry/raster-foundry/pull/2110)
- AST: Masking [\#2083](https://github.com/raster-foundry/raster-foundry/pull/2083)
- Feature/jis/dropbox export frontend [\#2063](https://github.com/raster-foundry/raster-foundry/pull/2063)
- Tool Reference nodes [\#2020](https://github.com/raster-foundry/raster-foundry/pull/2020)

## [0.16.0](https://github.com/raster-foundry/raster-foundry/tree/0.16.0) (2017-06-27)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.15.0...0.16.0)

**Merged pull requests:**

- Add nodata specification to ToolRun sources [\#2131](https://github.com/raster-foundry/raster-foundry/pull/2131)
- Promote Image.rawDataBytes to a Long [\#2128](https://github.com/raster-foundry/raster-foundry/pull/2128)
- Ingest seams fix [\#2118](https://github.com/raster-foundry/raster-foundry/pull/2118)
- Feature/lk/export mask [\#2116](https://github.com/raster-foundry/raster-foundry/pull/2116)
- List exports and download from project details [\#2105](https://github.com/raster-foundry/raster-foundry/pull/2105)
- Enable front end color auto correction. [\#2092](https://github.com/raster-foundry/raster-foundry/pull/2092)

## [0.15.0](https://github.com/raster-foundry/raster-foundry/tree/0.15.0) (2017-06-26)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.14.0...0.15.0)

**Merged pull requests:**

- Hide project navbar while project is loading [\#2108](https://github.com/raster-foundry/raster-foundry/pull/2108)
- Fix auth0 issues by upgrading lock library [\#2107](https://github.com/raster-foundry/raster-foundry/pull/2107)
- Add Algae Bloom Indices [\#2104](https://github.com/raster-foundry/raster-foundry/pull/2104)
- Improve Airflow DAG/Task Logging [\#2101](https://github.com/raster-foundry/raster-foundry/pull/2101)
- ColorCorrection improvements [\#2094](https://github.com/raster-foundry/raster-foundry/pull/2094)
- Link logo to home page [\#2093](https://github.com/raster-foundry/raster-foundry/pull/2093)
- Prompt before closing window while in upload [\#2088](https://github.com/raster-foundry/raster-foundry/pull/2088)
- Add  field to sources [\#2085](https://github.com/raster-foundry/raster-foundry/pull/2085)
- Document tool rendering options [\#2068](https://github.com/raster-foundry/raster-foundry/pull/2068)

## [0.14.0](https://github.com/raster-foundry/raster-foundry/tree/0.14.0) (2017-06-21)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.13.0...0.14.0)

**Merged pull requests:**

- Fix bad css merge [\#2067](https://github.com/raster-foundry/raster-foundry/pull/2067)
- Check for bad shape extraction [\#2065](https://github.com/raster-foundry/raster-foundry/pull/2065)
- AST: Overrides for "extra params" [\#2064](https://github.com/raster-foundry/raster-foundry/pull/2064)
- Use HTTPs for aerial map tiles [\#2062](https://github.com/raster-foundry/raster-foundry/pull/2062)
- Feature/lk/better map controls [\#2059](https://github.com/raster-foundry/raster-foundry/pull/2059)
- Install GDAL 2.1 in Airflow worker container image [\#2057](https://github.com/raster-foundry/raster-foundry/pull/2057)
- Add Constant LazyTile [\#2056](https://github.com/raster-foundry/raster-foundry/pull/2056)
- Move webpack manifest plugin to prod only [\#2036](https://github.com/raster-foundry/raster-foundry/pull/2036)
- Introduce new project export pane [\#2022](https://github.com/raster-foundry/raster-foundry/pull/2022)

## [0.13.0](https://github.com/raster-foundry/raster-foundry/tree/0.13.0) (2017-06-19)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.12.0...0.13.0)

**Merged pull requests:**

- Add compressed project-item [\#2051](https://github.com/raster-foundry/raster-foundry/pull/2051)
- updates the project page layout [\#2046](https://github.com/raster-foundry/raster-foundry/pull/2046)
- Fix Nginx HTTP virtual host collision [\#2045](https://github.com/raster-foundry/raster-foundry/pull/2045)
- Safer interpreters [\#1860](https://github.com/raster-foundry/raster-foundry/pull/1860)

## [0.12.0](https://github.com/raster-foundry/raster-foundry/tree/0.12.0) (2017-06-16)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.11.0...0.12.0)

**Merged pull requests:**

- Thumbnail base url typo fix [\#2037](https://github.com/raster-foundry/raster-foundry/pull/2037)
- Use https for base of landsat URLs [\#2034](https://github.com/raster-foundry/raster-foundry/pull/2034)
- updates tools page, fixes border-radius issue [\#2033](https://github.com/raster-foundry/raster-foundry/pull/2033)
- Omit port from Dropbox OAuth redirect URL [\#2028](https://github.com/raster-foundry/raster-foundry/pull/2028)
- Ingests Fix [\#2024](https://github.com/raster-foundry/raster-foundry/pull/2024)
- Use tile.band function [\#2023](https://github.com/raster-foundry/raster-foundry/pull/2023)
- Fix airflow export exit codes [\#2015](https://github.com/raster-foundry/raster-foundry/pull/2015)
- Increase Airflow parallelism [\#2010](https://github.com/raster-foundry/raster-foundry/pull/2010)
- Replace userService with authservice in datasourceService [\#2009](https://github.com/raster-foundry/raster-foundry/pull/2009)
- Add default exports output location during creation [\#1999](https://github.com/raster-foundry/raster-foundry/pull/1999)
- Correctly produce histograms for small projects [\#1988](https://github.com/raster-foundry/raster-foundry/pull/1988)
- Install Docker v17 [\#1987](https://github.com/raster-foundry/raster-foundry/pull/1987)
- Feature/lk/remove attr duplicate text [\#1986](https://github.com/raster-foundry/raster-foundry/pull/1986)
- Add heap properties to user log-in [\#1984](https://github.com/raster-foundry/raster-foundry/pull/1984)
- Update working links to homepage [\#1983](https://github.com/raster-foundry/raster-foundry/pull/1983)
- Add overrides.js.template for overriding select webpack global vars [\#1982](https://github.com/raster-foundry/raster-foundry/pull/1982)
- Add datasource creation modal [\#1981](https://github.com/raster-foundry/raster-foundry/pull/1981)
- Add dag run timeout to ingest scenes [\#1979](https://github.com/raster-foundry/raster-foundry/pull/1979)
- AOI Database & Endpoint fixes [\#1978](https://github.com/raster-foundry/raster-foundry/pull/1978)
- Use S3 to track export status jobs  [\#1976](https://github.com/raster-foundry/raster-foundry/pull/1976)
- Feature/lk/draw aois [\#1970](https://github.com/raster-foundry/raster-foundry/pull/1970)
- Airflow AOI task revisions [\#1969](https://github.com/raster-foundry/raster-foundry/pull/1969)
- Adjust Ingest Parallelism on Data Size [\#1967](https://github.com/raster-foundry/raster-foundry/pull/1967)
- Fix import modal [\#1959](https://github.com/raster-foundry/raster-foundry/pull/1959)
- Fix path for API spec publishing [\#1956](https://github.com/raster-foundry/raster-foundry/pull/1956)
- Add Min/Max support in RDD AST exports [\#1954](https://github.com/raster-foundry/raster-foundry/pull/1954)
- Link AoI endpoint to Project [\#1951](https://github.com/raster-foundry/raster-foundry/pull/1951)
- Rebuild node-sass after yarn install [\#1946](https://github.com/raster-foundry/raster-foundry/pull/1946)
- User session improvements [\#1943](https://github.com/raster-foundry/raster-foundry/pull/1943)
- Feature/lk/delete dead code [\#1942](https://github.com/raster-foundry/raster-foundry/pull/1942)
- Permission issues during the export job fix [\#1938](https://github.com/raster-foundry/raster-foundry/pull/1938)
- Allow for RAW exports of data [\#1937](https://github.com/raster-foundry/raster-foundry/pull/1937)
- Disable third party services in dev [\#1934](https://github.com/raster-foundry/raster-foundry/pull/1934)
- Tolerate missing EPSG code [\#1932](https://github.com/raster-foundry/raster-foundry/pull/1932)
- Add Dropbox Frontend views [\#1931](https://github.com/raster-foundry/raster-foundry/pull/1931)
- Update projects AoI fields [\#1929](https://github.com/raster-foundry/raster-foundry/pull/1929)
- Remove scene list mutation from scene selection modal [\#1927](https://github.com/raster-foundry/raster-foundry/pull/1927)
- adds readme section on theming and font family variables [\#1924](https://github.com/raster-foundry/raster-foundry/pull/1924)
- Feature/document ast [\#1923](https://github.com/raster-foundry/raster-foundry/pull/1923)
- Add logging to tile server routes [\#1922](https://github.com/raster-foundry/raster-foundry/pull/1922)
- Export jobs improvements [\#1920](https://github.com/raster-foundry/raster-foundry/pull/1920)
- Fix sidebar headers [\#1919](https://github.com/raster-foundry/raster-foundry/pull/1919)
- Feature/jis/dropbox finish auth endpoint [\#1918](https://github.com/raster-foundry/raster-foundry/pull/1918)
- Add documentation for dev database [\#1917](https://github.com/raster-foundry/raster-foundry/pull/1917)
- Fix Landsat8 thumbnail paths again [\#1916](https://github.com/raster-foundry/raster-foundry/pull/1916)
- Update .env.template [\#1914](https://github.com/raster-foundry/raster-foundry/pull/1914)
- Handling `ProjectRaster`s in AST Exports [\#1913](https://github.com/raster-foundry/raster-foundry/pull/1913)
- preps app for themeing [\#1912](https://github.com/raster-foundry/raster-foundry/pull/1912)
- Revert `toggle` component and add `toggle-ow` component [\#1911](https://github.com/raster-foundry/raster-foundry/pull/1911)
- Sentinel2 & Landsat8 minor fixes [\#1903](https://github.com/raster-foundry/raster-foundry/pull/1903)
- Fix bad Thumbnail urls in LC8 Imports [\#1899](https://github.com/raster-foundry/raster-foundry/pull/1899)
- Change AOI redirect. Show both tile url formats [\#1894](https://github.com/raster-foundry/raster-foundry/pull/1894)
- Remove unique constraint and update importers [\#1867](https://github.com/raster-foundry/raster-foundry/pull/1867)
- Add thread count on tile healthcheck [\#1865](https://github.com/raster-foundry/raster-foundry/pull/1865)
- Add multi-scene accept endpoint [\#1829](https://github.com/raster-foundry/raster-foundry/pull/1829)
- Feature/lk/put tiler behind nginx in dev [\#1806](https://github.com/raster-foundry/raster-foundry/pull/1806)

## [0.11.0](https://github.com/raster-foundry/raster-foundry/tree/0.11.0) (2017-06-01)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.10.1...0.11.0)

**Merged pull requests:**

- Update ingest location when updating scenes [\#1888](https://github.com/raster-foundry/raster-foundry/pull/1888)
- Set max zoom level to 30 instead of leaving undefined [\#1879](https://github.com/raster-foundry/raster-foundry/pull/1879)
- Update old ImportLandsat8 [\#1872](https://github.com/raster-foundry/raster-foundry/pull/1872)
- Add one-way binding to toggle [\#1871](https://github.com/raster-foundry/raster-foundry/pull/1871)
- Don't cache color-corrections [\#1870](https://github.com/raster-foundry/raster-foundry/pull/1870)
- Fix max zoom level [\#1868](https://github.com/raster-foundry/raster-foundry/pull/1868)
- Add Intercom websocket endpoints to content security policy [\#1866](https://github.com/raster-foundry/raster-foundry/pull/1866)
- Remove color corrections from datasources [\#1863](https://github.com/raster-foundry/raster-foundry/pull/1863)
- Fix typo in logger \(infoi =\> info\) [\#1859](https://github.com/raster-foundry/raster-foundry/pull/1859)
- Allow downloading of completed exports [\#1855](https://github.com/raster-foundry/raster-foundry/pull/1855)
- Bump Terraform version on Jenkins to 0.9.6 [\#1851](https://github.com/raster-foundry/raster-foundry/pull/1851)
- Check JSON parameters before writing them to DB [\#1812](https://github.com/raster-foundry/raster-foundry/pull/1812)
- Exports via RDD-based ASTs [\#1728](https://github.com/raster-foundry/raster-foundry/pull/1728)

## [0.10.1](https://github.com/raster-foundry/raster-foundry/tree/0.10.1) (2017-05-27)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.10.0...0.10.1)

**Merged pull requests:**

- Remove unecessary from\_string method [\#1853](https://github.com/raster-foundry/raster-foundry/pull/1853)

## [0.10.0](https://github.com/raster-foundry/raster-foundry/tree/0.10.0) (2017-05-26)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.9.1...0.10.0)

**Merged pull requests:**

- Feature/lk/default projects [\#1850](https://github.com/raster-foundry/raster-foundry/pull/1850)

## [0.9.1](https://github.com/raster-foundry/raster-foundry/tree/0.9.1) (2017-05-26)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.9.0...0.9.1)

**Merged pull requests:**

- Fix method call to get ingest status [\#1849](https://github.com/raster-foundry/raster-foundry/pull/1849)
- Update/fix color-mode selection [\#1846](https://github.com/raster-foundry/raster-foundry/pull/1846)
- Handle possible undefined return from project create modal [\#1845](https://github.com/raster-foundry/raster-foundry/pull/1845)
- Refactor color correction storage [\#1843](https://github.com/raster-foundry/raster-foundry/pull/1843)
- Fix object copied to clipboard in share modal [\#1842](https://github.com/raster-foundry/raster-foundry/pull/1842)
- Cleans up main project edit page [\#1827](https://github.com/raster-foundry/raster-foundry/pull/1827)
- C1 Scala Landsat8 import  [\#1821](https://github.com/raster-foundry/raster-foundry/pull/1821)
- Dropbox export [\#1809](https://github.com/raster-foundry/raster-foundry/pull/1809)

## [0.9.0](https://github.com/raster-foundry/raster-foundry/tree/0.9.0) (2017-05-25)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.8.0...0.9.0)

**Merged pull requests:**

- Avoid reducing over empty list [\#1840](https://github.com/raster-foundry/raster-foundry/pull/1840)
- Set data value explicitly [\#1831](https://github.com/raster-foundry/raster-foundry/pull/1831)
- Fix imports view dropdown menus [\#1814](https://github.com/raster-foundry/raster-foundry/pull/1814)
- Allow import into existing project [\#1813](https://github.com/raster-foundry/raster-foundry/pull/1813)
- Add approval\_required and start\_time fields to AoisToProjects model [\#1794](https://github.com/raster-foundry/raster-foundry/pull/1794)

## [0.8.0](https://github.com/raster-foundry/raster-foundry/tree/0.8.0) (2017-05-25)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.7.0...0.8.0)

**Merged pull requests:**

- Make the lab work [\#1833](https://github.com/raster-foundry/raster-foundry/pull/1833)
- Add tiffs to acceptable file patterns [\#1832](https://github.com/raster-foundry/raster-foundry/pull/1832)
- Tool source endpoint [\#1828](https://github.com/raster-foundry/raster-foundry/pull/1828)
- Add function to get raw tile for extent [\#1825](https://github.com/raster-foundry/raster-foundry/pull/1825)
- Fix Alex's merge conflict [\#1823](https://github.com/raster-foundry/raster-foundry/pull/1823)
- Change `ingested` filters to use`scene.ingestStatus` [\#1815](https://github.com/raster-foundry/raster-foundry/pull/1815)
- Use bulk-update for color mode changes [\#1810](https://github.com/raster-foundry/raster-foundry/pull/1810)
- Update color corrections on field blur, make debounce longer [\#1803](https://github.com/raster-foundry/raster-foundry/pull/1803)
- Add support for external S3 export [\#1799](https://github.com/raster-foundry/raster-foundry/pull/1799)
- New histogram with clipping [\#1793](https://github.com/raster-foundry/raster-foundry/pull/1793)
- Implement Robust Auto White-Balance [\#1787](https://github.com/raster-foundry/raster-foundry/pull/1787)
- Connect AOI front-end to API [\#1769](https://github.com/raster-foundry/raster-foundry/pull/1769)
- Make parallel ingest cooperate [\#1739](https://github.com/raster-foundry/raster-foundry/pull/1739)

## [0.7.0](https://github.com/raster-foundry/raster-foundry/tree/0.7.0) (2017-05-19)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.6.0...0.7.0)

**Merged pull requests:**

- Various Upload Processing Fixes [\#1795](https://github.com/raster-foundry/raster-foundry/pull/1795)
- Respond with 400 for interpreter errors [\#1786](https://github.com/raster-foundry/raster-foundry/pull/1786)
- Update swagger Spec Copy [\#1771](https://github.com/raster-foundry/raster-foundry/pull/1771)
- Use thumbnails in project list [\#1770](https://github.com/raster-foundry/raster-foundry/pull/1770)
- Force deletion of images that have multiple tags [\#1768](https://github.com/raster-foundry/raster-foundry/pull/1768)
- Add min/max tool operations [\#1767](https://github.com/raster-foundry/raster-foundry/pull/1767)
- Apply spec diff from azavea/raster-foundry-python-client\#4 [\#1766](https://github.com/raster-foundry/raster-foundry/pull/1766)
- fixes styling for aoi tag [\#1762](https://github.com/raster-foundry/raster-foundry/pull/1762)
- cleans up status tags styling [\#1759](https://github.com/raster-foundry/raster-foundry/pull/1759)
- Update swagger spec [\#1758](https://github.com/raster-foundry/raster-foundry/pull/1758)
- Add a scene owner filter to the browse view [\#1752](https://github.com/raster-foundry/raster-foundry/pull/1752)
- Add Dropbox credential field [\#1742](https://github.com/raster-foundry/raster-foundry/pull/1742)
- Feature/ak/single date picker [\#1733](https://github.com/raster-foundry/raster-foundry/pull/1733)
- Refactor RGB clipping [\#1708](https://github.com/raster-foundry/raster-foundry/pull/1708)
- Map Algebra AST evaluation [\#1441](https://github.com/raster-foundry/raster-foundry/pull/1441)

## [0.6.0](https://github.com/raster-foundry/raster-foundry/tree/0.6.0) (2017-05-15)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.5.0...0.6.0)

**Merged pull requests:**

- Add `pending` boolean query parameter to project scenes endpoint [\#1743](https://github.com/raster-foundry/raster-foundry/pull/1743)
- Minor frontend cleanup [\#1734](https://github.com/raster-foundry/raster-foundry/pull/1734)
- Add tiff as option for tile export [\#1725](https://github.com/raster-foundry/raster-foundry/pull/1725)
- Add initial UI for Area of Interest projects [\#1722](https://github.com/raster-foundry/raster-foundry/pull/1722)
- Fix Ingest Status Update [\#1716](https://github.com/raster-foundry/raster-foundry/pull/1716)
- adds a new calendar icon to icon-font [\#1714](https://github.com/raster-foundry/raster-foundry/pull/1714)
- Add Dropbox Setup [\#1713](https://github.com/raster-foundry/raster-foundry/pull/1713)
- AOI Scala airflow jobs implementation [\#1710](https://github.com/raster-foundry/raster-foundry/pull/1710)
- Copy plugins into airflow webserver when building [\#1709](https://github.com/raster-foundry/raster-foundry/pull/1709)
- updates project list and project detail ui [\#1706](https://github.com/raster-foundry/raster-foundry/pull/1706)
- Add state tags to various UI items [\#1703](https://github.com/raster-foundry/raster-foundry/pull/1703)
- Add Airflow Dag Trigger Endpoint [\#1702](https://github.com/raster-foundry/raster-foundry/pull/1702)
- Add per-scenes histogram endpoint [\#1701](https://github.com/raster-foundry/raster-foundry/pull/1701)
- Frontend fixes [\#1700](https://github.com/raster-foundry/raster-foundry/pull/1700)
- updates fontello. updates homedashboar. className changes [\#1699](https://github.com/raster-foundry/raster-foundry/pull/1699)
- Implement data-down pattern for tool def [\#1688](https://github.com/raster-foundry/raster-foundry/pull/1688)
- Implement individual RGB color channel clipping [\#1675](https://github.com/raster-foundry/raster-foundry/pull/1675)
- Make project list reload when project created [\#1661](https://github.com/raster-foundry/raster-foundry/pull/1661)

## [0.5.0](https://github.com/raster-foundry/raster-foundry/tree/0.5.0) (2017-05-05)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.4.0...0.5.0)

**Merged pull requests:**

- Few Small Bugfixes [\#1686](https://github.com/raster-foundry/raster-foundry/pull/1686)
- Remove remaining references to app-clients [\#1684](https://github.com/raster-foundry/raster-foundry/pull/1684)
- Remove Python API client from main repository [\#1681](https://github.com/raster-foundry/raster-foundry/pull/1681)
- Group by IDs to construct Scene.WithRelateds instead of by scenes [\#1673](https://github.com/raster-foundry/raster-foundry/pull/1673)
- Add support for single band raster import [\#1662](https://github.com/raster-foundry/raster-foundry/pull/1662)
- Import Sentinel2 dag to use jar [\#1615](https://github.com/raster-foundry/raster-foundry/pull/1615)
- Sentinel2 import [\#1611](https://github.com/raster-foundry/raster-foundry/pull/1611)
- Implement export workflow [\#1609](https://github.com/raster-foundry/raster-foundry/pull/1609)

## [0.4.0](https://github.com/raster-foundry/raster-foundry/tree/0.4.0) (2017-05-03)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.3.0...0.4.0)

**Merged pull requests:**

- Fix tile server location in lab UI [\#1655](https://github.com/raster-foundry/raster-foundry/pull/1655)
- Fix reversal of `crs` and `extent\_crs` [\#1614](https://github.com/raster-foundry/raster-foundry/pull/1614)
- Update scala dockerignore to include new subprojects [\#1612](https://github.com/raster-foundry/raster-foundry/pull/1612)

## [0.3.0](https://github.com/raster-foundry/raster-foundry/tree/0.3.0) (2017-04-28)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.2.0...0.3.0)

**Merged pull requests:**

- Import landsat8 dag to use jar [\#1597](https://github.com/raster-foundry/raster-foundry/pull/1597)
- Connect imported scenes to project [\#1594](https://github.com/raster-foundry/raster-foundry/pull/1594)
- Saturation slider [\#1587](https://github.com/raster-foundry/raster-foundry/pull/1587)
- Add Saturation parameter to color correction [\#1585](https://github.com/raster-foundry/raster-foundry/pull/1585)
- Add Explicit Owner Field/Filter [\#1580](https://github.com/raster-foundry/raster-foundry/pull/1580)
- Update color-correction UI [\#1578](https://github.com/raster-foundry/raster-foundry/pull/1578)
- Scala Landsat8 import [\#1576](https://github.com/raster-foundry/raster-foundry/pull/1576)
- Project export modal [\#1575](https://github.com/raster-foundry/raster-foundry/pull/1575)
- Initial Python Client [\#1573](https://github.com/raster-foundry/raster-foundry/pull/1573)
- Fix shapeless EMR error [\#1568](https://github.com/raster-foundry/raster-foundry/pull/1568)
- Show scenes already added to project [\#1567](https://github.com/raster-foundry/raster-foundry/pull/1567)
- Add batch jar to airflow [\#1565](https://github.com/raster-foundry/raster-foundry/pull/1565)
- Remove unused items and fix project deletion [\#1563](https://github.com/raster-foundry/raster-foundry/pull/1563)
- Use the EMR step API for ingests instead of batch [\#1562](https://github.com/raster-foundry/raster-foundry/pull/1562)
- Feature/lk/fix map memoryleak [\#1559](https://github.com/raster-foundry/raster-foundry/pull/1559)
- Initialize `registerClick` flag in browse [\#1556](https://github.com/raster-foundry/raster-foundry/pull/1556)
- Use per-user feature flags on the frontend [\#1548](https://github.com/raster-foundry/raster-foundry/pull/1548)
- Scene detail modal [\#1546](https://github.com/raster-foundry/raster-foundry/pull/1546)
- Fix project detail page [\#1542](https://github.com/raster-foundry/raster-foundry/pull/1542)
- Add swagger vendor extensions [\#1503](https://github.com/raster-foundry/raster-foundry/pull/1503)
- Add java dependency back to airflow [\#1502](https://github.com/raster-foundry/raster-foundry/pull/1502)
- Add stub dags for area of interest monitoring [\#1493](https://github.com/raster-foundry/raster-foundry/pull/1493)
- Add API endpoint for organization feature overrides [\#1492](https://github.com/raster-foundry/raster-foundry/pull/1492)
- Unify Ingest/Export Projects [\#1486](https://github.com/raster-foundry/raster-foundry/pull/1486)
- Fix long compile times caused by circe auto derivation [\#1485](https://github.com/raster-foundry/raster-foundry/pull/1485)
- Feature/lk/new project ui panels [\#1484](https://github.com/raster-foundry/raster-foundry/pull/1484)
- Service-layer implementation of organization overrides for feature flags [\#1483](https://github.com/raster-foundry/raster-foundry/pull/1483)
- Export datamodels, specs, api routes [\#1479](https://github.com/raster-foundry/raster-foundry/pull/1479)
- New project item component [\#1477](https://github.com/raster-foundry/raster-foundry/pull/1477)
- Add org-\>feature mapping table [\#1474](https://github.com/raster-foundry/raster-foundry/pull/1474)
- Reorganize Imports page [\#1470](https://github.com/raster-foundry/raster-foundry/pull/1470)
- Fix bug in datasource init for uploads [\#1466](https://github.com/raster-foundry/raster-foundry/pull/1466)
- Add status-filter for Uploads [\#1464](https://github.com/raster-foundry/raster-foundry/pull/1464)
- Refactor Ingest Definition Creation [\#1463](https://github.com/raster-foundry/raster-foundry/pull/1463)
- Split Tileserver [\#1461](https://github.com/raster-foundry/raster-foundry/pull/1461)
- Minimize Airflow container rebuilds; upgrade Spark container images [\#1460](https://github.com/raster-foundry/raster-foundry/pull/1460)
- Add a custom rejection handler for malformed requests [\#1458](https://github.com/raster-foundry/raster-foundry/pull/1458)
- Request previous day's landsat scenes [\#1457](https://github.com/raster-foundry/raster-foundry/pull/1457)
- Use bulk color-correction endpoint [\#1456](https://github.com/raster-foundry/raster-foundry/pull/1456)
- Allow import from project creation [\#1451](https://github.com/raster-foundry/raster-foundry/pull/1451)
- AOI Swagger and Endpoints [\#1450](https://github.com/raster-foundry/raster-foundry/pull/1450)
- Remove use of NODE\_ENV where possible [\#1448](https://github.com/raster-foundry/raster-foundry/pull/1448)
- Fix config error page not showing [\#1436](https://github.com/raster-foundry/raster-foundry/pull/1436)
- Add "closes" in PR template [\#1432](https://github.com/raster-foundry/raster-foundry/pull/1432)
- Ensure thumbnail creation works for raw geotiff import [\#1406](https://github.com/raster-foundry/raster-foundry/pull/1406)
- Add gamma correction linking [\#1403](https://github.com/raster-foundry/raster-foundry/pull/1403)
- Add deployment for Swagger spec [\#1402](https://github.com/raster-foundry/raster-foundry/pull/1402)
- Project GeoTiff export [\#1401](https://github.com/raster-foundry/raster-foundry/pull/1401)
- Local upload UI [\#1396](https://github.com/raster-foundry/raster-foundry/pull/1396)
- AOI Types, Tables, and Migrations [\#1395](https://github.com/raster-foundry/raster-foundry/pull/1395)
- Add visibility field to Uploads table and model [\#1393](https://github.com/raster-foundry/raster-foundry/pull/1393)
- Add batch color-correction endpoint [\#1391](https://github.com/raster-foundry/raster-foundry/pull/1391)
- Fix unused import [\#1389](https://github.com/raster-foundry/raster-foundry/pull/1389)
- Use uploads API for scene imports [\#1388](https://github.com/raster-foundry/raster-foundry/pull/1388)
- Directives Follow Up: Limit to Root Members, Single Result [\#1383](https://github.com/raster-foundry/raster-foundry/pull/1383)
- Make migration link relative [\#1382](https://github.com/raster-foundry/raster-foundry/pull/1382)
- Remove Client Error Notifications from Rollbar [\#1379](https://github.com/raster-foundry/raster-foundry/pull/1379)
- Don't enforce strict url matching [\#1378](https://github.com/raster-foundry/raster-foundry/pull/1378)
- Feature/lk/reduce grid refreshes [\#1377](https://github.com/raster-foundry/raster-foundry/pull/1377)
- Fix datasource filter clearing [\#1374](https://github.com/raster-foundry/raster-foundry/pull/1374)
- Fix rollbar and Intercom instantiation errors [\#1372](https://github.com/raster-foundry/raster-foundry/pull/1372)
- Rework airflow geotiff import to poll for uploads [\#1371](https://github.com/raster-foundry/raster-foundry/pull/1371)
- Store feature flags in database [\#1366](https://github.com/raster-foundry/raster-foundry/pull/1366)
- Use date range picker instead of year / month filters [\#1363](https://github.com/raster-foundry/raster-foundry/pull/1363)
- Add feature flag for displaying Tools [\#1359](https://github.com/raster-foundry/raster-foundry/pull/1359)
- Add Authorize Directive, Limit Access to Members and Super Users [\#1358](https://github.com/raster-foundry/raster-foundry/pull/1358)
- Add batched correction to API spec [\#1357](https://github.com/raster-foundry/raster-foundry/pull/1357)
- cleans up the new home page [\#1356](https://github.com/raster-foundry/raster-foundry/pull/1356)
- Set environment variables within the VM [\#1352](https://github.com/raster-foundry/raster-foundry/pull/1352)
- Intercom integration [\#1348](https://github.com/raster-foundry/raster-foundry/pull/1348)
- Initialize Rollbar only once in Airflow [\#1346](https://github.com/raster-foundry/raster-foundry/pull/1346)
- Scoped S3 Credentials [\#1342](https://github.com/raster-foundry/raster-foundry/pull/1342)
- Fix login redirect [\#1337](https://github.com/raster-foundry/raster-foundry/pull/1337)
- Add Rollbar integration to front-end [\#1331](https://github.com/raster-foundry/raster-foundry/pull/1331)
- Add API/Models for Uploads [\#1330](https://github.com/raster-foundry/raster-foundry/pull/1330)
- Update README: Fix Environment Variables [\#1329](https://github.com/raster-foundry/raster-foundry/pull/1329)
- Remove unused UI elements [\#1328](https://github.com/raster-foundry/raster-foundry/pull/1328)
- Standardize Swagger spec [\#1325](https://github.com/raster-foundry/raster-foundry/pull/1325)
- Handle app boot without cache [\#1324](https://github.com/raster-foundry/raster-foundry/pull/1324)
- Add TMS stress testing with Gatling [\#1313](https://github.com/raster-foundry/raster-foundry/pull/1313)
- Fix deletion handling on api token component [\#1312](https://github.com/raster-foundry/raster-foundry/pull/1312)
- Bump Terraform version on Jenkins to 0.9.1 [\#1308](https://github.com/raster-foundry/raster-foundry/pull/1308)
- Add top-level project dropdown [\#1300](https://github.com/raster-foundry/raster-foundry/pull/1300)
- Add ADR for Exports design [\#1286](https://github.com/raster-foundry/raster-foundry/pull/1286)
- Update Map Token UI [\#1284](https://github.com/raster-foundry/raster-foundry/pull/1284)
- Remove foul language from project source code [\#1279](https://github.com/raster-foundry/raster-foundry/pull/1279)
- Remove Many-to-Many Relationship between Users and Organizations [\#1276](https://github.com/raster-foundry/raster-foundry/pull/1276)
- Feature/jis/replace spray with circe everywhere [\#1275](https://github.com/raster-foundry/raster-foundry/pull/1275)
- Add datasource management UI [\#1274](https://github.com/raster-foundry/raster-foundry/pull/1274)
- Add `uploads` to Swagger Spec [\#1271](https://github.com/raster-foundry/raster-foundry/pull/1271)
- Improve Non-Azavea Provisioning [\#1269](https://github.com/raster-foundry/raster-foundry/pull/1269)
- Include both database and auth0 users in /me endpoint [\#1268](https://github.com/raster-foundry/raster-foundry/pull/1268)
- Add ADR for initial upload/import support [\#1263](https://github.com/raster-foundry/raster-foundry/pull/1263)
- Update license copyright [\#1259](https://github.com/raster-foundry/raster-foundry/pull/1259)
- Authorize Tile Access with Map Tokens [\#1257](https://github.com/raster-foundry/raster-foundry/pull/1257)
- Populate blog posts on homepage [\#1256](https://github.com/raster-foundry/raster-foundry/pull/1256)
- Fix filtering for map tokens by project ID [\#1251](https://github.com/raster-foundry/raster-foundry/pull/1251)
- Add panning and zooming to lab UI [\#1250](https://github.com/raster-foundry/raster-foundry/pull/1250)
- Map Token management views [\#1248](https://github.com/raster-foundry/raster-foundry/pull/1248)
- Delay parsing of putative ASTs [\#1244](https://github.com/raster-foundry/raster-foundry/pull/1244)
- Add MapToken database migrations, api endpoints [\#1243](https://github.com/raster-foundry/raster-foundry/pull/1243)
- Add Facebook mention-bot configuration [\#1240](https://github.com/raster-foundry/raster-foundry/pull/1240)
- Add share policy to project share modal [\#1239](https://github.com/raster-foundry/raster-foundry/pull/1239)
- Remove bintray warning [\#1238](https://github.com/raster-foundry/raster-foundry/pull/1238)
- Update README [\#1237](https://github.com/raster-foundry/raster-foundry/pull/1237)
- Add new create project modal flow [\#1236](https://github.com/raster-foundry/raster-foundry/pull/1236)
- New project page + navbar [\#1235](https://github.com/raster-foundry/raster-foundry/pull/1235)
- Redirect to login page on 401 [\#1234](https://github.com/raster-foundry/raster-foundry/pull/1234)
- Add tile visibility to Project model & migrations [\#1232](https://github.com/raster-foundry/raster-foundry/pull/1232)
- Create home page for updated UI [\#1231](https://github.com/raster-foundry/raster-foundry/pull/1231)
- Print full stack trace for rollbar [\#1230](https://github.com/raster-foundry/raster-foundry/pull/1230)
- Add spec for map token to swagger [\#1228](https://github.com/raster-foundry/raster-foundry/pull/1228)
- Windowed GeoTiff Ingest [\#1224](https://github.com/raster-foundry/raster-foundry/pull/1224)
- Remove call to get JWT in airflow scheduler [\#1223](https://github.com/raster-foundry/raster-foundry/pull/1223)
- Fix token deletion [\#1211](https://github.com/raster-foundry/raster-foundry/pull/1211)
- Tiler handle missing Histogram, Ingest Location \(introduces OptionT usage\) [\#1193](https://github.com/raster-foundry/raster-foundry/pull/1193)
- Add ADR on refactoring tests for convenience [\#1188](https://github.com/raster-foundry/raster-foundry/pull/1188)

## [0.2.0](https://github.com/raster-foundry/raster-foundry/tree/0.2.0) (2017-03-09)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.1.0...0.2.0)

**Merged pull requests:**

- Update Color Correct API [\#1199](https://github.com/raster-foundry/raster-foundry/pull/1199)
- Fixup swagger spec [\#1198](https://github.com/raster-foundry/raster-foundry/pull/1198)
- Remove default values for ingest env vars [\#1196](https://github.com/raster-foundry/raster-foundry/pull/1196)
- Include related items in tool response [\#1195](https://github.com/raster-foundry/raster-foundry/pull/1195)
- RFML tool AST and Parser [\#1192](https://github.com/raster-foundry/raster-foundry/pull/1192)
- Increase memcached max object size in development [\#1191](https://github.com/raster-foundry/raster-foundry/pull/1191)
- Return empty png rather than empty response [\#1189](https://github.com/raster-foundry/raster-foundry/pull/1189)
- Fix docker-compose typo [\#1186](https://github.com/raster-foundry/raster-foundry/pull/1186)
- ADR 0014 Revisiting api documentation [\#1185](https://github.com/raster-foundry/raster-foundry/pull/1185)
- Fix ingest definition parsing and add tests [\#1184](https://github.com/raster-foundry/raster-foundry/pull/1184)
- Fix typo in sbt clean command [\#1183](https://github.com/raster-foundry/raster-foundry/pull/1183)
- Update Metadata Imports [\#1181](https://github.com/raster-foundry/raster-foundry/pull/1181)
- Improve failed ingest handling [\#1180](https://github.com/raster-foundry/raster-foundry/pull/1180)
- Work around layer deletion bug in GT [\#1179](https://github.com/raster-foundry/raster-foundry/pull/1179)
- Allow updating user profile and password from frontend [\#1177](https://github.com/raster-foundry/raster-foundry/pull/1177)
- Authorization logic for adding scenes to a project [\#1173](https://github.com/raster-foundry/raster-foundry/pull/1173)
- Operationalize Scene Ingest Trigger [\#1171](https://github.com/raster-foundry/raster-foundry/pull/1171)
- Update Nginx set\_real\_ip\_from for public subnets [\#1170](https://github.com/raster-foundry/raster-foundry/pull/1170)
- Return error code when webpack build fails [\#1169](https://github.com/raster-foundry/raster-foundry/pull/1169)
- Streamline requests for color-correction [\#1165](https://github.com/raster-foundry/raster-foundry/pull/1165)
- Move app-frontend to docker-compose.yml [\#1162](https://github.com/raster-foundry/raster-foundry/pull/1162)
- Move and rename app server to api [\#1161](https://github.com/raster-foundry/raster-foundry/pull/1161)
- Cache layer prefix lookups [\#1160](https://github.com/raster-foundry/raster-foundry/pull/1160)
- Bump Terraform version to 0.8.7 [\#1158](https://github.com/raster-foundry/raster-foundry/pull/1158)
- Populate color mode dropdown from API [\#1157](https://github.com/raster-foundry/raster-foundry/pull/1157)
- Add Session storage of map filters [\#1156](https://github.com/raster-foundry/raster-foundry/pull/1156)
- Feature/jis/validate swagger spec in ci [\#1155](https://github.com/raster-foundry/raster-foundry/pull/1155)
- Remove placeholders + fix scene removal [\#1154](https://github.com/raster-foundry/raster-foundry/pull/1154)
- Add loading indicator to map component [\#1151](https://github.com/raster-foundry/raster-foundry/pull/1151)
- Add login page [\#1150](https://github.com/raster-foundry/raster-foundry/pull/1150)
- Utilize server computed project extent [\#1149](https://github.com/raster-foundry/raster-foundry/pull/1149)
- Kayak Geotiff import DAG [\#1147](https://github.com/raster-foundry/raster-foundry/pull/1147)
- Fix auth0 management tokens [\#1140](https://github.com/raster-foundry/raster-foundry/pull/1140)
- Add ability for tiler to use bounding box instead of tile coordinate [\#1138](https://github.com/raster-foundry/raster-foundry/pull/1138)
- ToolRun API [\#1137](https://github.com/raster-foundry/raster-foundry/pull/1137)
- Fix metadata urls in sentinel2 import [\#1134](https://github.com/raster-foundry/raster-foundry/pull/1134)
- Upgrade akka-http [\#1133](https://github.com/raster-foundry/raster-foundry/pull/1133)
- Add extent field and computation to project [\#1132](https://github.com/raster-foundry/raster-foundry/pull/1132)
- Return 409 response on duplicate scene creation [\#1131](https://github.com/raster-foundry/raster-foundry/pull/1131)
- Memcached wrapper for safe getOrElseUpdate [\#1127](https://github.com/raster-foundry/raster-foundry/pull/1127)
- Bulk add scenes to a project based on a query [\#1125](https://github.com/raster-foundry/raster-foundry/pull/1125)

## [0.1.0](https://github.com/raster-foundry/raster-foundry/tree/0.1.0) (2017-02-16)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.0.1...0.1.0)

**Merged pull requests:**

- Add Scene Ingest Trigger [\#1122](https://github.com/raster-foundry/raster-foundry/pull/1122)
- Avoid division by 0 in color correction [\#1121](https://github.com/raster-foundry/raster-foundry/pull/1121)
- Add json definition to tool model [\#1114](https://github.com/raster-foundry/raster-foundry/pull/1114)
- Fix landsat8 tile footprint computation [\#1112](https://github.com/raster-foundry/raster-foundry/pull/1112)
- Create ScenesToProjects with a default color correction [\#1111](https://github.com/raster-foundry/raster-foundry/pull/1111)
- Add validation to project create forms [\#1110](https://github.com/raster-foundry/raster-foundry/pull/1110)
- Add support for non-Base64 encoded JWT client secrets [\#1109](https://github.com/raster-foundry/raster-foundry/pull/1109)
- Add share option to diagram context menu [\#1107](https://github.com/raster-foundry/raster-foundry/pull/1107)
- fixed majority of current known browser issues [\#1106](https://github.com/raster-foundry/raster-foundry/pull/1106)
- Add custom tile URL format to publish modal [\#1105](https://github.com/raster-foundry/raster-foundry/pull/1105)
- Fix scene grid param mismatch [\#1104](https://github.com/raster-foundry/raster-foundry/pull/1104)
- Create and alter permissions on AIRFLOW\_HOME [\#1099](https://github.com/raster-foundry/raster-foundry/pull/1099)
- Create symlink for migration 41 [\#1097](https://github.com/raster-foundry/raster-foundry/pull/1097)
- Build diagram from tool JSON [\#1091](https://github.com/raster-foundry/raster-foundry/pull/1091)
- Filter library scenes to scenes which are created by user [\#1085](https://github.com/raster-foundry/raster-foundry/pull/1085)
- Prevent filters and grid layer from initializing before login [\#1081](https://github.com/raster-foundry/raster-foundry/pull/1081)
- Make Scene filters inclusive [\#1070](https://github.com/raster-foundry/raster-foundry/pull/1070)
- Add token parameter to thumbnail urls that start with '/' [\#1068](https://github.com/raster-foundry/raster-foundry/pull/1068)
- Feature/custom nodata [\#1067](https://github.com/raster-foundry/raster-foundry/pull/1067)
- Clean up leaflet-side-by-side [\#1066](https://github.com/raster-foundry/raster-foundry/pull/1066)
- Feature/lk/maintain zoom level node preview [\#1065](https://github.com/raster-foundry/raster-foundry/pull/1065)
- Move app-frontend service to test Docker Compose config [\#1064](https://github.com/raster-foundry/raster-foundry/pull/1064)
- Fix missing fields in Scene Detail views [\#1063](https://github.com/raster-foundry/raster-foundry/pull/1063)
- Add ToolRun model [\#1062](https://github.com/raster-foundry/raster-foundry/pull/1062)
- Move home directory of Airflow user [\#1057](https://github.com/raster-foundry/raster-foundry/pull/1057)
- Increase RouteTestTimeout to 5s for all specs [\#1056](https://github.com/raster-foundry/raster-foundry/pull/1056)
- Update datasource filter and references [\#1052](https://github.com/raster-foundry/raster-foundry/pull/1052)
- Consistently use sbt-extras; move assembly builds to cipublish [\#1050](https://github.com/raster-foundry/raster-foundry/pull/1050)
- Add footprint generation from geotiffs [\#1049](https://github.com/raster-foundry/raster-foundry/pull/1049)
- Remove redundant boolean check in editor [\#1045](https://github.com/raster-foundry/raster-foundry/pull/1045)
- Add thumbnail creation to geotiff processing [\#1044](https://github.com/raster-foundry/raster-foundry/pull/1044)
- Feature/jis/add datasource endpoint [\#1043](https://github.com/raster-foundry/raster-foundry/pull/1043)
- Add stubbed DAG for ingesting Scenes [\#1042](https://github.com/raster-foundry/raster-foundry/pull/1042)
- Feature/lk/set scene ingest status [\#1040](https://github.com/raster-foundry/raster-foundry/pull/1040)
- Allow layers to be overwritten on ingest [\#1037](https://github.com/raster-foundry/raster-foundry/pull/1037)
- Create scene filter for ingestStatus [\#1035](https://github.com/raster-foundry/raster-foundry/pull/1035)
- Simplify tiler URLs [\#1034](https://github.com/raster-foundry/raster-foundry/pull/1034)
- Fix disabling color correction groups [\#1033](https://github.com/raster-foundry/raster-foundry/pull/1033)
- Add project select modal in editor [\#1030](https://github.com/raster-foundry/raster-foundry/pull/1030)
- Feature/ingest tifftags [\#1029](https://github.com/raster-foundry/raster-foundry/pull/1029)
- Private thumbnail proxy [\#1028](https://github.com/raster-foundry/raster-foundry/pull/1028)
- Feature/jis/add ingest status to scenes [\#1027](https://github.com/raster-foundry/raster-foundry/pull/1027)
- Add project modal to scene detail page [\#1026](https://github.com/raster-foundry/raster-foundry/pull/1026)
- Move Rollbar to common project, add to tiler [\#1025](https://github.com/raster-foundry/raster-foundry/pull/1025)
- Set leakDetectionThreshold [\#1024](https://github.com/raster-foundry/raster-foundry/pull/1024)
- Add token param to tile requests in lab [\#1023](https://github.com/raster-foundry/raster-foundry/pull/1023)
- Add google fonts to template [\#1022](https://github.com/raster-foundry/raster-foundry/pull/1022)
- UI cleanup and new fonts [\#1020](https://github.com/raster-foundry/raster-foundry/pull/1020)
- Instruct Webpack to halt and report failures as soon as they occur [\#1017](https://github.com/raster-foundry/raster-foundry/pull/1017)
- Split Swagger services out into their own Docker Compose config [\#1016](https://github.com/raster-foundry/raster-foundry/pull/1016)
- Bump versions of Docker Engine and Compose [\#1014](https://github.com/raster-foundry/raster-foundry/pull/1014)
- Fix Tile Authentication [\#1012](https://github.com/raster-foundry/raster-foundry/pull/1012)
- Add share page [\#1000](https://github.com/raster-foundry/raster-foundry/pull/1000)
- Add import functionality for S3-hosted GeoTiffs [\#997](https://github.com/raster-foundry/raster-foundry/pull/997)
- Add nodes for input projects to diagram [\#994](https://github.com/raster-foundry/raster-foundry/pull/994)
- Prevent duplication of effort on expensive cache misses [\#993](https://github.com/raster-foundry/raster-foundry/pull/993)
- Add initial filter values and single year filter mode [\#976](https://github.com/raster-foundry/raster-foundry/pull/976)
- Show selected false color composite in dropdown [\#975](https://github.com/raster-foundry/raster-foundry/pull/975)
- Bump geotrellis to 1.0 release [\#969](https://github.com/raster-foundry/raster-foundry/pull/969)
- Fix duplicated image links by adding "distinct" to query [\#966](https://github.com/raster-foundry/raster-foundry/pull/966)
- Use tag for caching mosaicDefinition, default to none [\#963](https://github.com/raster-foundry/raster-foundry/pull/963)
- Feature/ak/add datasource model [\#962](https://github.com/raster-foundry/raster-foundry/pull/962)
- Add NDVI difference endpoint with reclassify parts [\#950](https://github.com/raster-foundry/raster-foundry/pull/950)
- Add 'ingestLocation' field with filtering to scenes [\#948](https://github.com/raster-foundry/raster-foundry/pull/948)
- Fix tiler deadlocks when interacting with the database [\#945](https://github.com/raster-foundry/raster-foundry/pull/945)
- Add toggles to disable color correction filters by group [\#941](https://github.com/raster-foundry/raster-foundry/pull/941)
- Add heap to app-frontend [\#939](https://github.com/raster-foundry/raster-foundry/pull/939)
- Add day-of-month filter to backend [\#938](https://github.com/raster-foundry/raster-foundry/pull/938)
- Add day-of-month range filter [\#937](https://github.com/raster-foundry/raster-foundry/pull/937)
- WIP: Add diagram node preview and comparison [\#936](https://github.com/raster-foundry/raster-foundry/pull/936)
- Add tile authentication [\#935](https://github.com/raster-foundry/raster-foundry/pull/935)
- Add rollbar to app-server [\#932](https://github.com/raster-foundry/raster-foundry/pull/932)
- Persist Scene footprints when selected for color correction [\#931](https://github.com/raster-foundry/raster-foundry/pull/931)
- Create input parameters sidebar [\#930](https://github.com/raster-foundry/raster-foundry/pull/930)
- Feature/cmb/add fonts use yarn [\#927](https://github.com/raster-foundry/raster-foundry/pull/927)
- Add context menus and selected cell to diagram [\#926](https://github.com/raster-foundry/raster-foundry/pull/926)
- Remove unused links in library [\#924](https://github.com/raster-foundry/raster-foundry/pull/924)
- Add diagram for NDVI Change [\#923](https://github.com/raster-foundry/raster-foundry/pull/923)
- Refactor dead-end modals to lab and editor [\#921](https://github.com/raster-foundry/raster-foundry/pull/921)
- Feature/cmb/disable hist wireup publish [\#919](https://github.com/raster-foundry/raster-foundry/pull/919)
- Fix browse scene thumbnail persistence [\#917](https://github.com/raster-foundry/raster-foundry/pull/917)
- use mosaic tile server [\#915](https://github.com/raster-foundry/raster-foundry/pull/915)
- Retain bbox query parameter and tiny fixes [\#914](https://github.com/raster-foundry/raster-foundry/pull/914)
- Clean up copy on library scene list [\#911](https://github.com/raster-foundry/raster-foundry/pull/911)
- Approximately Clip Thumbnails to Data Footprint [\#908](https://github.com/raster-foundry/raster-foundry/pull/908)
- Make color correction selection grid finer [\#906](https://github.com/raster-foundry/raster-foundry/pull/906)
- Prevent duplicate controls on map [\#904](https://github.com/raster-foundry/raster-foundry/pull/904)
- Implement shallow NDVI tool [\#903](https://github.com/raster-foundry/raster-foundry/pull/903)
- Cleanup of the app for the demo [\#896](https://github.com/raster-foundry/raster-foundry/pull/896)
- Use memcached in tile server [\#893](https://github.com/raster-foundry/raster-foundry/pull/893)
- Add project select modal to editor [\#891](https://github.com/raster-foundry/raster-foundry/pull/891)
- Exclude transitive postgresql dependency [\#890](https://github.com/raster-foundry/raster-foundry/pull/890)
- Add light vs dark basemap control to map [\#889](https://github.com/raster-foundry/raster-foundry/pull/889)
- Add grid filter to scene browse [\#887](https://github.com/raster-foundry/raster-foundry/pull/887)
- Replace union with or filter for user visibility [\#886](https://github.com/raster-foundry/raster-foundry/pull/886)
- Project Mosaic Tiler [\#883](https://github.com/raster-foundry/raster-foundry/pull/883)
- Persist & Retrieve Color Corrections [\#881](https://github.com/raster-foundry/raster-foundry/pull/881)
- Add publish modal to project details and editor [\#879](https://github.com/raster-foundry/raster-foundry/pull/879)
- Fix page param for 'try again' requests [\#877](https://github.com/raster-foundry/raster-foundry/pull/877)
- Feature/increase scene grid resolution [\#874](https://github.com/raster-foundry/raster-foundry/pull/874)
- Feature/js/clean up thumbnail drawing logic [\#871](https://github.com/raster-foundry/raster-foundry/pull/871)
- Add experimental histogram summation for color correction [\#870](https://github.com/raster-foundry/raster-foundry/pull/870)
- Feature/filter bad landsat images [\#868](https://github.com/raster-foundry/raster-foundry/pull/868)
- Add loading indicator to color correct histogram [\#867](https://github.com/raster-foundry/raster-foundry/pull/867)
- Add Memcached service to Docker Compose [\#866](https://github.com/raster-foundry/raster-foundry/pull/866)
- Add mosaic/color definition persistence [\#864](https://github.com/raster-foundry/raster-foundry/pull/864)
- Fix production build styling [\#862](https://github.com/raster-foundry/raster-foundry/pull/862)
- Add feature flag for 'make source histogram' in color correction [\#861](https://github.com/raster-foundry/raster-foundry/pull/861)
- Hide pagination on adding scenes to project modal [\#860](https://github.com/raster-foundry/raster-foundry/pull/860)
- Rename to 'Tool Catalog' and 'Lab' [\#859](https://github.com/raster-foundry/raster-foundry/pull/859)
- Bump Terraform version to 0.8.2 [\#858](https://github.com/raster-foundry/raster-foundry/pull/858)
- WIP: added nonfunctioning select all btn to scene browser [\#842](https://github.com/raster-foundry/raster-foundry/pull/842)
- Feature/zoom to active browse scene [\#838](https://github.com/raster-foundry/raster-foundry/pull/838)
- Add mosaic parameters page [\#822](https://github.com/raster-foundry/raster-foundry/pull/822)
- Feature/thumbnail service [\#821](https://github.com/raster-foundry/raster-foundry/pull/821)
- Show scene thumbnails on map instead of data footprint [\#820](https://github.com/raster-foundry/raster-foundry/pull/820)
- Connect model market to API [\#819](https://github.com/raster-foundry/raster-foundry/pull/819)
- Add migration to provide 3 sample tools [\#817](https://github.com/raster-foundry/raster-foundry/pull/817)
- Fix min/max slider in project/edit [\#816](https://github.com/raster-foundry/raster-foundry/pull/816)
- Make Spark execution environment ADR-0013 [\#815](https://github.com/raster-foundry/raster-foundry/pull/815)
- Refactor map container [\#813](https://github.com/raster-foundry/raster-foundry/pull/813)
- Add migration to provide 3 sample tools [\#811](https://github.com/raster-foundry/raster-foundry/pull/811)
- Add model builder page with example UI [\#808](https://github.com/raster-foundry/raster-foundry/pull/808)
- Add ADR 0012 Map Component Api [\#803](https://github.com/raster-foundry/raster-foundry/pull/803)
- Increase route test timeout to 3 seconds [\#802](https://github.com/raster-foundry/raster-foundry/pull/802)
- Add a --no-airflow option to ./scripts/server [\#800](https://github.com/raster-foundry/raster-foundry/pull/800)
- Add ADR for Spark execution environment [\#793](https://github.com/raster-foundry/raster-foundry/pull/793)
- Fit map to scenes being color-corrected [\#792](https://github.com/raster-foundry/raster-foundry/pull/792)
- Add mosaic mask view [\#790](https://github.com/raster-foundry/raster-foundry/pull/790)
- Update dependency pinning for frontend [\#788](https://github.com/raster-foundry/raster-foundry/pull/788)
- cleaned up the color correction UI [\#786](https://github.com/raster-foundry/raster-foundry/pull/786)
- Render actual histogram for first scene in cc [\#785](https://github.com/raster-foundry/raster-foundry/pull/785)
- Hide pagination on editor lists when appropriate [\#783](https://github.com/raster-foundry/raster-foundry/pull/783)
- Allow selecting scenes to color correct by grid [\#780](https://github.com/raster-foundry/raster-foundry/pull/780)
- cleaned up project scene list ui [\#779](https://github.com/raster-foundry/raster-foundry/pull/779)
- Display scene footprints on hover for color correction [\#778](https://github.com/raster-foundry/raster-foundry/pull/778)
- Revert d3 dep to v3.4.4 [\#776](https://github.com/raster-foundry/raster-foundry/pull/776)
- Redirect from add-to-project modal to project list/detail [\#775](https://github.com/raster-foundry/raster-foundry/pull/775)
- Feature/feature flags [\#772](https://github.com/raster-foundry/raster-foundry/pull/772)
- Mosaic UI [\#771](https://github.com/raster-foundry/raster-foundry/pull/771)
- Fix styling issues [\#770](https://github.com/raster-foundry/raster-foundry/pull/770)
- Fix old footprints which were placed in wrong column [\#768](https://github.com/raster-foundry/raster-foundry/pull/768)
- Feature/update importer with data footprints [\#767](https://github.com/raster-foundry/raster-foundry/pull/767)
- Write ADR for Tool UI framework [\#765](https://github.com/raster-foundry/raster-foundry/pull/765)
- Feature/hookup color correction [\#762](https://github.com/raster-foundry/raster-foundry/pull/762)
- Add data\_footprint to scenes [\#759](https://github.com/raster-foundry/raster-foundry/pull/759)
- Add Tool Category route [\#758](https://github.com/raster-foundry/raster-foundry/pull/758)
- Eliminate extra and duplicate requests [\#757](https://github.com/raster-foundry/raster-foundry/pull/757)
- Add db model/endpoints for geospatial tools [\#756](https://github.com/raster-foundry/raster-foundry/pull/756)
- Add project name editing [\#754](https://github.com/raster-foundry/raster-foundry/pull/754)
- Filter scene query by createdAt [\#752](https://github.com/raster-foundry/raster-foundry/pull/752)
- Create ingest definition from scene ID [\#751](https://github.com/raster-foundry/raster-foundry/pull/751)
- Feature/rename models to tools [\#750](https://github.com/raster-foundry/raster-foundry/pull/750)
- Mosaic ColorCorrecting Tiler [\#747](https://github.com/raster-foundry/raster-foundry/pull/747)
- WIP: Showing scene footprint on hover [\#746](https://github.com/raster-foundry/raster-foundry/pull/746)
- Rename buckets to projects [\#741](https://github.com/raster-foundry/raster-foundry/pull/741)
- Add grid endpoint and frontend [\#739](https://github.com/raster-foundry/raster-foundry/pull/739)
- Add Ingest Parameters [\#723](https://github.com/raster-foundry/raster-foundry/pull/723)
- Limit API lists to public / owned items [\#722](https://github.com/raster-foundry/raster-foundry/pull/722)
- Add histogram and thumbnail endpoints in tiler app [\#719](https://github.com/raster-foundry/raster-foundry/pull/719)
- Reverse proxy to tiler from Nginx; add stub service health check [\#714](https://github.com/raster-foundry/raster-foundry/pull/714)
- Wait for login before requesting scenes [\#713](https://github.com/raster-foundry/raster-foundry/pull/713)
- Authenticate everything except insecure endpoints [\#711](https://github.com/raster-foundry/raster-foundry/pull/711)
- Feature/add spark worker [\#707](https://github.com/raster-foundry/raster-foundry/pull/707)
- Fix re-adding scenes to buckets [\#706](https://github.com/raster-foundry/raster-foundry/pull/706)
- Add bucket filtering to scene specific query params [\#704](https://github.com/raster-foundry/raster-foundry/pull/704)
- Remove ingest test [\#681](https://github.com/raster-foundry/raster-foundry/pull/681)
- Ensure that Airflow doesn't run as root [\#679](https://github.com/raster-foundry/raster-foundry/pull/679)
- Feature/add model tags endpoint [\#671](https://github.com/raster-foundry/raster-foundry/pull/671)
- Change 'Landsat-8' to 'Landsat 8' on the frontend [\#669](https://github.com/raster-foundry/raster-foundry/pull/669)
- Add Airflow remote log configuration via environment [\#661](https://github.com/raster-foundry/raster-foundry/pull/661)
- Update Airflow configuration; add Flower reverse proxy [\#658](https://github.com/raster-foundry/raster-foundry/pull/658)
- Optimize server side grouping of scenes [\#657](https://github.com/raster-foundry/raster-foundry/pull/657)
- Bucket editing / Color correction [\#655](https://github.com/raster-foundry/raster-foundry/pull/655)
- Add spec for /scene-grids/ [\#651](https://github.com/raster-foundry/raster-foundry/pull/651)
- Add model tags to database [\#649](https://github.com/raster-foundry/raster-foundry/pull/649)
- Add instructions for backfilling DAG runs and DAG schedules [\#648](https://github.com/raster-foundry/raster-foundry/pull/648)
- Tile Server \[WIP\] [\#647](https://github.com/raster-foundry/raster-foundry/pull/647)
- Add model search and detail views [\#646](https://github.com/raster-foundry/raster-foundry/pull/646)
- Airflow reverse proxy and base URL override [\#644](https://github.com/raster-foundry/raster-foundry/pull/644)
- Add Airflow DAGs at container image build time [\#643](https://github.com/raster-foundry/raster-foundry/pull/643)
- Bump Terraform to version 0.7.9 [\#642](https://github.com/raster-foundry/raster-foundry/pull/642)
- Use Band model, resolution, and metadata file on import [\#641](https://github.com/raster-foundry/raster-foundry/pull/641)
- Run migrations for every test run [\#640](https://github.com/raster-foundry/raster-foundry/pull/640)
- Standardize on internal database and cache hostname [\#638](https://github.com/raster-foundry/raster-foundry/pull/638)
- Filter scenes by map bounds [\#636](https://github.com/raster-foundry/raster-foundry/pull/636)
- Add tile ingest [\#621](https://github.com/raster-foundry/raster-foundry/pull/621)
- Reduce Slick Logging [\#616](https://github.com/raster-foundry/raster-foundry/pull/616)
- Use latitude / longitude coordinates in footprints [\#615](https://github.com/raster-foundry/raster-foundry/pull/615)
- Update Swagger Specification and Add Model Catalog [\#613](https://github.com/raster-foundry/raster-foundry/pull/613)
- Add download modal component [\#610](https://github.com/raster-foundry/raster-foundry/pull/610)
- Filter Scenes by resolution \(and other image parameters\) [\#604](https://github.com/raster-foundry/raster-foundry/pull/604)
- Fix Jenkins executor reset when running Ansible [\#602](https://github.com/raster-foundry/raster-foundry/pull/602)
- Batch add/remove bucket scenes [\#598](https://github.com/raster-foundry/raster-foundry/pull/598)
- Increase database copy timeout [\#597](https://github.com/raster-foundry/raster-foundry/pull/597)
- Add migrations documentation [\#596](https://github.com/raster-foundry/raster-foundry/pull/596)
- Feature/add bands model [\#595](https://github.com/raster-foundry/raster-foundry/pull/595)
- Bump Terraform to version 0.7.7 [\#592](https://github.com/raster-foundry/raster-foundry/pull/592)
- Add metadata files fields to Scene and Image [\#590](https://github.com/raster-foundry/raster-foundry/pull/590)
- Move resolutionMeters to image [\#588](https://github.com/raster-foundry/raster-foundry/pull/588)
- Make slider css more specific [\#581](https://github.com/raster-foundry/raster-foundry/pull/581)
- Manually set GIT\_COMMIT in Jenkinsfile [\#579](https://github.com/raster-foundry/raster-foundry/pull/579)
- Allow deleting buckets linked to scenes [\#578](https://github.com/raster-foundry/raster-foundry/pull/578)
- Feature/refresh token frontend [\#574](https://github.com/raster-foundry/raster-foundry/pull/574)
- Upgrade auth0 to auth.lock v10 [\#568](https://github.com/raster-foundry/raster-foundry/pull/568)
- Not forward 5432 and add a psql helper script [\#567](https://github.com/raster-foundry/raster-foundry/pull/567)
- Update Route Ordering [\#566](https://github.com/raster-foundry/raster-foundry/pull/566)
- Use Docker Compose to bring down services [\#565](https://github.com/raster-foundry/raster-foundry/pull/565)
- Add an initial style guide and update PR template [\#564](https://github.com/raster-foundry/raster-foundry/pull/564)
- Return 400, not 500 for invalid tokens [\#563](https://github.com/raster-foundry/raster-foundry/pull/563)
- Add comments to Jenkinsfile; limit Slack messages to errors [\#559](https://github.com/raster-foundry/raster-foundry/pull/559)
- Add filter pane component and hook up to browse view [\#555](https://github.com/raster-foundry/raster-foundry/pull/555)
- Make changes required for ECS deployment [\#546](https://github.com/raster-foundry/raster-foundry/pull/546)
- Bulk Scene Operations [\#544](https://github.com/raster-foundry/raster-foundry/pull/544)
- Remove `restart-jenkins` cronjob, add Jenkins user to sudoers [\#543](https://github.com/raster-foundry/raster-foundry/pull/543)
- Fix pycparser installation [\#542](https://github.com/raster-foundry/raster-foundry/pull/542)
- Remove owner role and replace with user [\#540](https://github.com/raster-foundry/raster-foundry/pull/540)
- Add sbt container [\#539](https://github.com/raster-foundry/raster-foundry/pull/539)
- Bump terraform install version and azavea.terraform role version [\#538](https://github.com/raster-foundry/raster-foundry/pull/538)
- Library bucket view and scene/bucket modals [\#537](https://github.com/raster-foundry/raster-foundry/pull/537)
- Bump the node version [\#535](https://github.com/raster-foundry/raster-foundry/pull/535)
- Remove uglifyjs from production [\#534](https://github.com/raster-foundry/raster-foundry/pull/534)
- Add images on Landsat 8 scene creation [\#529](https://github.com/raster-foundry/raster-foundry/pull/529)
- Support Refresh Tokens [\#528](https://github.com/raster-foundry/raster-foundry/pull/528)
- Add ADR for domain layout [\#525](https://github.com/raster-foundry/raster-foundry/pull/525)
- Restart Jenkins every 3 hours to stop branch indexing [\#522](https://github.com/raster-foundry/raster-foundry/pull/522)
- Create config service / provider [\#517](https://github.com/raster-foundry/raster-foundry/pull/517)
- Refactor datamodel and remove code-gen [\#516](https://github.com/raster-foundry/raster-foundry/pull/516)
- Exclude signatures of signed jars during assembly [\#511](https://github.com/raster-foundry/raster-foundry/pull/511)
- Add pull request template [\#509](https://github.com/raster-foundry/raster-foundry/pull/509)
- Add embedded map w/ geojson to library/scenes/{id} [\#508](https://github.com/raster-foundry/raster-foundry/pull/508)
- Bump terraform version to 0.7.4 [\#505](https://github.com/raster-foundry/raster-foundry/pull/505)
- updated modal styles [\#503](https://github.com/raster-foundry/raster-foundry/pull/503)
- Feature/hookup main view [\#491](https://github.com/raster-foundry/raster-foundry/pull/491)
- Feature/add landsat8 scene creation [\#490](https://github.com/raster-foundry/raster-foundry/pull/490)
- Feature/cmb/move footprint field [\#489](https://github.com/raster-foundry/raster-foundry/pull/489)
- Feature/update prototypes [\#486](https://github.com/raster-foundry/raster-foundry/pull/486)
- Add thumbnails endpoint and tests [\#485](https://github.com/raster-foundry/raster-foundry/pull/485)
- Add airflow tasks/rf functions to import sentinel 2 data [\#481](https://github.com/raster-foundry/raster-foundry/pull/481)
- Add model and API for images [\#480](https://github.com/raster-foundry/raster-foundry/pull/480)
- updated prototypes for rf-v2 [\#479](https://github.com/raster-foundry/raster-foundry/pull/479)
- Make PGUtilsSpec unmixable [\#478](https://github.com/raster-foundry/raster-foundry/pull/478)
- Feature/footprints endpoint [\#476](https://github.com/raster-foundry/raster-foundry/pull/476)
- Use Groovy block syntax in Jenkinsfile for stages [\#473](https://github.com/raster-foundry/raster-foundry/pull/473)
- Add buckets and scenes API endpoints [\#463](https://github.com/raster-foundry/raster-foundry/pull/463)
- Add footprint models [\#462](https://github.com/raster-foundry/raster-foundry/pull/462)
- Create migration for thumbnails model [\#461](https://github.com/raster-foundry/raster-foundry/pull/461)
- Feature/cmb/add buckets model [\#458](https://github.com/raster-foundry/raster-foundry/pull/458)
- Create tests for all existing endpoints [\#457](https://github.com/raster-foundry/raster-foundry/pull/457)
- Add Scenes Model [\#456](https://github.com/raster-foundry/raster-foundry/pull/456)
- Add logging to existing endpoints [\#450](https://github.com/raster-foundry/raster-foundry/pull/450)
- Remove swagger UI from nginx config [\#448](https://github.com/raster-foundry/raster-foundry/pull/448)
- Generate fresh databases \(with migrations applied\) while testing [\#447](https://github.com/raster-foundry/raster-foundry/pull/447)
- Add Organizations endpoint [\#446](https://github.com/raster-foundry/raster-foundry/pull/446)
- Add buckets, refine layers/images API endpoints [\#445](https://github.com/raster-foundry/raster-foundry/pull/445)
- Feature/add landsat8 footprints [\#444](https://github.com/raster-foundry/raster-foundry/pull/444)
- Create many to many user-org relationship w/ metadata [\#443](https://github.com/raster-foundry/raster-foundry/pull/443)
- Fix error on fresh provisions [\#442](https://github.com/raster-foundry/raster-foundry/pull/442)
- Run migrations on setup [\#441](https://github.com/raster-foundry/raster-foundry/pull/441)
- Add support for infrastructure management via Jenkinsfile [\#440](https://github.com/raster-foundry/raster-foundry/pull/440)
- Create users automatically [\#439](https://github.com/raster-foundry/raster-foundry/pull/439)
- Add pagination to users endpoint [\#438](https://github.com/raster-foundry/raster-foundry/pull/438)
- Hide fields in API [\#434](https://github.com/raster-foundry/raster-foundry/pull/434)
- Add support for publishing container images to ECR [\#433](https://github.com/raster-foundry/raster-foundry/pull/433)
- Add authentication directive to authenticate users [\#432](https://github.com/raster-foundry/raster-foundry/pull/432)
- WIP: Create Jenkins Pipeline Part 1 - CIBuild [\#431](https://github.com/raster-foundry/raster-foundry/pull/431)
- Add Jenkins build dependencies [\#430](https://github.com/raster-foundry/raster-foundry/pull/430)
- Add Spark Standalone cluster with README [\#424](https://github.com/raster-foundry/raster-foundry/pull/424)
- Add users endpoint [\#422](https://github.com/raster-foundry/raster-foundry/pull/422)
- Add provisioning for Jenkins using Ansible Dynamic Inventory. [\#419](https://github.com/raster-foundry/raster-foundry/pull/419)
- Add auth0 authentication to login page. [\#418](https://github.com/raster-foundry/raster-foundry/pull/418)
- Add initial airflow scaffolding [\#407](https://github.com/raster-foundry/raster-foundry/pull/407)
- Add support for database migrations [\#406](https://github.com/raster-foundry/raster-foundry/pull/406)
- Add development setup for front-end [\#404](https://github.com/raster-foundry/raster-foundry/pull/404)
- Add page prototypes [\#401](https://github.com/raster-foundry/raster-foundry/pull/401)
- Add layers API spec [\#399](https://github.com/raster-foundry/raster-foundry/pull/399)
- Add initial Auth0 settings to application [\#397](https://github.com/raster-foundry/raster-foundry/pull/397)
- Static asset pipeline [\#396](https://github.com/raster-foundry/raster-foundry/pull/396)
- ADR for static asset pipeline [\#395](https://github.com/raster-foundry/raster-foundry/pull/395)
- Feature/add user api spec [\#393](https://github.com/raster-foundry/raster-foundry/pull/393)
- Add ADR for CI server [\#392](https://github.com/raster-foundry/raster-foundry/pull/392)
- Add Initial Application Server with `akka-http` [\#391](https://github.com/raster-foundry/raster-foundry/pull/391)
- Add ADR for web backend language and framework [\#390](https://github.com/raster-foundry/raster-foundry/pull/390)
- Add frontend framework choice ADR [\#376](https://github.com/raster-foundry/raster-foundry/pull/376)
- Add testing philosophy ADR [\#375](https://github.com/raster-foundry/raster-foundry/pull/375)
- ADR - API Design/Documentation Framework [\#374](https://github.com/raster-foundry/raster-foundry/pull/374)
- Add authentication ADR [\#373](https://github.com/raster-foundry/raster-foundry/pull/373)
- Reset to blank project to start Phase II [\#372](https://github.com/raster-foundry/raster-foundry/pull/372)

## [0.0.1](https://github.com/raster-foundry/raster-foundry/tree/0.0.1) (2016-07-14)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/31e56cea5aaa742dbcf162900326afbcf9adb497...0.0.1)

**Merged pull requests:**

- Add favicon [\#355](https://github.com/raster-foundry/raster-foundry/pull/355)
- Fix EMR add\_steps function [\#351](https://github.com/raster-foundry/raster-foundry/pull/351)
- Add exif data formatting [\#350](https://github.com/raster-foundry/raster-foundry/pull/350)
- Add transparent "no data" layer [\#349](https://github.com/raster-foundry/raster-foundry/pull/349)
- Fix column alignment on layer detail panel [\#348](https://github.com/raster-foundry/raster-foundry/pull/348)
- Fix IOError during thumbnail phase [\#347](https://github.com/raster-foundry/raster-foundry/pull/347)
- cleaned up login pages [\#345](https://github.com/raster-foundry/raster-foundry/pull/345)
- Minor updates to swagger.yaml  [\#338](https://github.com/raster-foundry/raster-foundry/pull/338)
- Add "zoom to extent" buttons [\#337](https://github.com/raster-foundry/raster-foundry/pull/337)
- Feature/lol/image metadata [\#335](https://github.com/raster-foundry/raster-foundry/pull/335)
- Upload status from Evaporate [\#327](https://github.com/raster-foundry/raster-foundry/pull/327)
- Only validate images once [\#322](https://github.com/raster-foundry/raster-foundry/pull/322)
- Reset heartbeat when resetting layer. [\#320](https://github.com/raster-foundry/raster-foundry/pull/320)
- Order layers and expand processing block [\#317](https://github.com/raster-foundry/raster-foundry/pull/317)
- Allow user to dismiss own layers [\#316](https://github.com/raster-foundry/raster-foundry/pull/316)
- Feature/lol/fix retry on complete [\#315](https://github.com/raster-foundry/raster-foundry/pull/315)
- Delete validation job when successful [\#311](https://github.com/raster-foundry/raster-foundry/pull/311)
- Split up thumbnail job [\#308](https://github.com/raster-foundry/raster-foundry/pull/308)
- Save layer thumbnail [\#305](https://github.com/raster-foundry/raster-foundry/pull/305)
- Serve placeholder and fonts over HTTPS [\#302](https://github.com/raster-foundry/raster-foundry/pull/302)
- Setup EC2 instance store for thumbnail processing [\#301](https://github.com/raster-foundry/raster-foundry/pull/301)
- Add m3.medium to EC2 instance types [\#299](https://github.com/raster-foundry/raster-foundry/pull/299)
- Add logging message [\#294](https://github.com/raster-foundry/raster-foundry/pull/294)
- Install Pillow 3.1.0.dev0 [\#293](https://github.com/raster-foundry/raster-foundry/pull/293)
- Use AWS session security token for S3 upload requests [\#292](https://github.com/raster-foundry/raster-foundry/pull/292)
- Switch basemap to HTTPS endpoints. [\#291](https://github.com/raster-foundry/raster-foundry/pull/291)
- Temporarily disable SMTP email backend on staging/production [\#288](https://github.com/raster-foundry/raster-foundry/pull/288)
- Feature/lol/new status format ui [\#283](https://github.com/raster-foundry/raster-foundry/pull/283)
- Fix dismiss [\#282](https://github.com/raster-foundry/raster-foundry/pull/282)
- Add support for S3 HTTPS URI [\#281](https://github.com/raster-foundry/raster-foundry/pull/281)
- Add scripts/poll.sh  [\#279](https://github.com/raster-foundry/raster-foundry/pull/279)
- Use vsicurl to validate images [\#274](https://github.com/raster-foundry/raster-foundry/pull/274)
- Feature/lol/new status format [\#273](https://github.com/raster-foundry/raster-foundry/pull/273)
- Prevent job conflicts [\#272](https://github.com/raster-foundry/raster-foundry/pull/272)
- Make copied images public-read [\#271](https://github.com/raster-foundry/raster-foundry/pull/271)
- Kill timeout if layer has finished processing [\#269](https://github.com/raster-foundry/raster-foundry/pull/269)
- Add Retry link for failed layers [\#268](https://github.com/raster-foundry/raster-foundry/pull/268)
- Add missing spark submit options [\#267](https://github.com/raster-foundry/raster-foundry/pull/267)
- Queue EMR handoff and thumbnail job at same time [\#260](https://github.com/raster-foundry/raster-foundry/pull/260)
- Remove milliseconds from logging date format [\#258](https://github.com/raster-foundry/raster-foundry/pull/258)
- Add more INFO logging messages [\#256](https://github.com/raster-foundry/raster-foundry/pull/256)
- Add support for launching stacks namespaced by color [\#255](https://github.com/raster-foundry/raster-foundry/pull/255)
- Feature/lol/cluster heartbeat task [\#254](https://github.com/raster-foundry/raster-foundry/pull/254)
- Improve validation [\#253](https://github.com/raster-foundry/raster-foundry/pull/253)
- Change S3 bucket name for AWS deployments [\#252](https://github.com/raster-foundry/raster-foundry/pull/252)
- Cast AWS\_EMR\_DEBUG to boolean [\#251](https://github.com/raster-foundry/raster-foundry/pull/251)
- Use correct layer status [\#250](https://github.com/raster-foundry/raster-foundry/pull/250)
- Update status during EMR steps [\#249](https://github.com/raster-foundry/raster-foundry/pull/249)
- Add monitoring endpoint. [\#243](https://github.com/raster-foundry/raster-foundry/pull/243)
- Remove execute bit from source code file [\#241](https://github.com/raster-foundry/raster-foundry/pull/241)
- Make working files public-read only. [\#239](https://github.com/raster-foundry/raster-foundry/pull/239)
- Add support for AMI pruning [\#231](https://github.com/raster-foundry/raster-foundry/pull/231)
- Remove dark boundary on thumbnails [\#230](https://github.com/raster-foundry/raster-foundry/pull/230)
- Add support for Route 53 DNS updates [\#224](https://github.com/raster-foundry/raster-foundry/pull/224)
- Disable MultiAZ Amazon RDS [\#223](https://github.com/raster-foundry/raster-foundry/pull/223)
- Allow blank on source\_s3\_bucket\_key [\#222](https://github.com/raster-foundry/raster-foundry/pull/222)
- Add EMR module and wire up handoff job [\#221](https://github.com/raster-foundry/raster-foundry/pull/221)
- Add support for worker servers [\#215](https://github.com/raster-foundry/raster-foundry/pull/215)
- Upgrade queue processor to boto3 [\#214](https://github.com/raster-foundry/raster-foundry/pull/214)
- S3 imports [\#213](https://github.com/raster-foundry/raster-foundry/pull/213)
- Fix placeholder text issue on Safari. [\#208](https://github.com/raster-foundry/raster-foundry/pull/208)
- Feature/lol/only allow tifs [\#206](https://github.com/raster-foundry/raster-foundry/pull/206)
- Feature/lol/date format error [\#204](https://github.com/raster-foundry/raster-foundry/pull/204)
- Remove redis & change django cache to Postgres. [\#200](https://github.com/raster-foundry/raster-foundry/pull/200)
- Start worker process during provisioning [\#199](https://github.com/raster-foundry/raster-foundry/pull/199)
- Create thumbnails  [\#197](https://github.com/raster-foundry/raster-foundry/pull/197)
- Add support for application servers [\#196](https://github.com/raster-foundry/raster-foundry/pull/196)
- Add support RDS PostgreSQL database [\#195](https://github.com/raster-foundry/raster-foundry/pull/195)
- Add support for Route 53 private hosted zone [\#194](https://github.com/raster-foundry/raster-foundry/pull/194)
- Add VPC CloudFormation stack [\#192](https://github.com/raster-foundry/raster-foundry/pull/192)
- Feature/lol/error messages [\#191](https://github.com/raster-foundry/raster-foundry/pull/191)
- Add support for creating application and worker AMIs [\#190](https://github.com/raster-foundry/raster-foundry/pull/190)
- Hide activation, forgot, and agreed checkbox [\#189](https://github.com/raster-foundry/raster-foundry/pull/189)
- Feature/lol/dismiss to delete failed jobs [\#184](https://github.com/raster-foundry/raster-foundry/pull/184)
- Click to delete layer [\#181](https://github.com/raster-foundry/raster-foundry/pull/181)
- Confirm logout if uploading [\#180](https://github.com/raster-foundry/raster-foundry/pull/180)
- Use known EC2 location as temp dir. [\#178](https://github.com/raster-foundry/raster-foundry/pull/178)
- Show error for failed layer [\#177](https://github.com/raster-foundry/raster-foundry/pull/177)
- Remove extra dot in s3 file name [\#173](https://github.com/raster-foundry/raster-foundry/pull/173)
- Feature/lol/verify images [\#172](https://github.com/raster-foundry/raster-foundry/pull/172)
- Remove unused options and update text [\#166](https://github.com/raster-foundry/raster-foundry/pull/166)
- Strip whitespace from tags. [\#165](https://github.com/raster-foundry/raster-foundry/pull/165)
- Send images when posting layers [\#163](https://github.com/raster-foundry/raster-foundry/pull/163)
- Wire up layer detail and image meta data panels [\#159](https://github.com/raster-foundry/raster-foundry/pull/159)
- Fix favorited layers not appearing as favorited [\#151](https://github.com/raster-foundry/raster-foundry/pull/151)
- Fix incorrect bundled animation CSS in watch mode [\#146](https://github.com/raster-foundry/raster-foundry/pull/146)
- Script creation of S3 and SQS resources [\#142](https://github.com/raster-foundry/raster-foundry/pull/142)
- Add better support for livereload [\#138](https://github.com/raster-foundry/raster-foundry/pull/138)
- Feature/lol/worker agent [\#136](https://github.com/raster-foundry/raster-foundry/pull/136)
- Processing Status [\#135](https://github.com/raster-foundry/raster-foundry/pull/135)
- Fix screen transitions [\#134](https://github.com/raster-foundry/raster-foundry/pull/134)
- fixed tags field appearance, fixes \#122 [\#133](https://github.com/raster-foundry/raster-foundry/pull/133)
- Fix background video reset on login screens [\#128](https://github.com/raster-foundry/raster-foundry/pull/128)
- Create a bare bones worker. [\#127](https://github.com/raster-foundry/raster-foundry/pull/127)
- Feature/lol/server side sorting [\#126](https://github.com/raster-foundry/raster-foundry/pull/126)
- Feature/lol/paginiation [\#124](https://github.com/raster-foundry/raster-foundry/pull/124)
- Feature/lol/client side file validation [\#112](https://github.com/raster-foundry/raster-foundry/pull/112)
- Add mock-geoprocessing service [\#109](https://github.com/raster-foundry/raster-foundry/pull/109)
- Send CSRF token on app reload [\#108](https://github.com/raster-foundry/raster-foundry/pull/108)
- Style password screen [\#106](https://github.com/raster-foundry/raster-foundry/pull/106)
- Consider anystring@anystring.anystring a valid email [\#105](https://github.com/raster-foundry/raster-foundry/pull/105)
- Wire up import modal [\#103](https://github.com/raster-foundry/raster-foundry/pull/103)
- Enable saving/loading layer favorites [\#97](https://github.com/raster-foundry/raster-foundry/pull/97)
- Enable toggling tile layers [\#96](https://github.com/raster-foundry/raster-foundry/pull/96)
- Trigger URL change when switching tabs  [\#95](https://github.com/raster-foundry/raster-foundry/pull/95)
- Enable scrolling in layer selector component [\#93](https://github.com/raster-foundry/raster-foundry/pull/93)
- Wire up endpoints to layer selector [\#92](https://github.com/raster-foundry/raster-foundry/pull/92)
- Add account screens [\#90](https://github.com/raster-foundry/raster-foundry/pull/90)
- Make upload work by preventing default action [\#87](https://github.com/raster-foundry/raster-foundry/pull/87)
- Remove projects from menu dropdown [\#75](https://github.com/raster-foundry/raster-foundry/pull/75)
- Remove user ping [\#74](https://github.com/raster-foundry/raster-foundry/pull/74)
- Fix unit tests [\#73](https://github.com/raster-foundry/raster-foundry/pull/73)
- Drag and drop files into upload modal [\#72](https://github.com/raster-foundry/raster-foundry/pull/72)
- Feature/lol/layer selector controls [\#66](https://github.com/raster-foundry/raster-foundry/pull/66)
- Remove leaflet-draw from bundle script. [\#64](https://github.com/raster-foundry/raster-foundry/pull/64)
- Add registration functionality [\#63](https://github.com/raster-foundry/raster-foundry/pull/63)
- Feature/lol/create layer selector component [\#62](https://github.com/raster-foundry/raster-foundry/pull/62)
- Feature/lol/s3 uuid [\#61](https://github.com/raster-foundry/raster-foundry/pull/61)
- Remove DRF [\#60](https://github.com/raster-foundry/raster-foundry/pull/60)
- Login and logout [\#59](https://github.com/raster-foundry/raster-foundry/pull/59)
- Remove leaflet draw. [\#41](https://github.com/raster-foundry/raster-foundry/pull/41)
- Feature/lol/s3 uploads [\#40](https://github.com/raster-foundry/raster-foundry/pull/40)
- Rest API for Layer operations [\#39](https://github.com/raster-foundry/raster-foundry/pull/39)
- Lint [\#38](https://github.com/raster-foundry/raster-foundry/pull/38)
- Create library sidebar JSX from prototype [\#36](https://github.com/raster-foundry/raster-foundry/pull/36)
- Convert login page prototype to JSX [\#34](https://github.com/raster-foundry/raster-foundry/pull/34)
- Add skeleton JS application [\#32](https://github.com/raster-foundry/raster-foundry/pull/32)
- Lint [\#31](https://github.com/raster-foundry/raster-foundry/pull/31)
- Fix Jenkins build [\#30](https://github.com/raster-foundry/raster-foundry/pull/30)
- Add bundle.sh with React support [\#29](https://github.com/raster-foundry/raster-foundry/pull/29)
- Stub out Django models [\#28](https://github.com/raster-foundry/raster-foundry/pull/28)
- Update README [\#27](https://github.com/raster-foundry/raster-foundry/pull/27)
- Setup dev env [\#16](https://github.com/raster-foundry/raster-foundry/pull/16)



* *This Change Log was automatically generated by [github_changelog_generator](https://github.com/skywinder/Github-Changelog-Generator)*

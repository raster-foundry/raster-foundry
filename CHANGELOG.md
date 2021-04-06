# Change Log
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Changed
- Update default color space clipping and expose QPs for tuning [#5561](https://github.com/raster-foundry/raster-foundry/pull/5561)

## [1.61.2] - 2021-03-30
### Changed
- Campaign creates can include an owner [#5558](https://github.com/raster-foundry/raster-foundry/pull/5558)

### Fixed
- Copy and routing for campaign and annotation projects were synchronized with the frontend app [#5558](https://github.com/raster-foundry/raster-foundry/pull/5558)

## [1.61.1] - 2021-03-10
### Fixed
- Restored ability to disable color correction, since simplified color correction darkened some client imagery [#5556](https://github.com/raster-foundry/raster-foundry/pull/5556)
- Allowed tasks with parents not to be automatically filtered out of task queries [#5556](https://github.com/raster-foundry/raster-foundry/pull/5556)

### Changed
- Updated setup instructions and scripts [#5544](https://github.com/raster-foundry/raster-foundry/pull/5544)

## [1.61.0] - 2021-03-04
### Changed
- Label class groups can have campaign IDs edited [#5548](https://github.com/raster-foundry/raster-foundry/pull/5548)
- Update boto3 file upload method to support uploading large COG after processing [#5553](https://github.com/raster-foundry/raster-foundry/pull/5553)
- Update annotation project GET endpoint to read from the right source of label class summary [#5551](https://github.com/raster-foundry/raster-foundry/pull/5551)
- Simplify backsplash color correct [#5554](https://github.com/raster-foundry/raster-foundry/pull/5554)

### Added
- Add migration to cascade delete label class group on campaign deletion [#5552](https://github.com/raster-foundry/raster-foundry/pull/5552)

## [1.60.1] - 2021-02-23
### Fixed
- Remove interior rings from data-footprint before generating task grid [#5550](https://github.com/raster-foundry/raster-foundry/pull/5550)

## [1.60.0] - 2021-02-19
### Changed
- Use campaign URL in intercom message when applicable [#5547](https://github.com/raster-foundry/raster-foundry/pull/5547)

### Fixed
- Removed holes from union of scene footprints before task grid construction [#5549](https://github.com/raster-foundry/raster-foundry/pull/5549)

## [1.59.0] - 2021-02-17
### Added
- Added an endpoint for campaign label class group summary [#5541](https://github.com/raster-foundry/raster-foundry/pull/5541)

### Fixed
- STAC exports for annotation projects without RF projects are no longer catalogs without children [#5540](https://github.com/raster-foundry/raster-foundry/pull/5540)
- Added retry and caching to a flaky integration test [#5545](https://github.com/raster-foundry/raster-foundry/pull/5545)

## [1.58.0] - 2021-01-13
### Added
- Backsplash server does its own access logging [#5531](https://github.com/raster-foundry/raster-foundry/pull/5531)
- Include annotation project id + TMS triple in traces for MVT requests [#5533](https://github.com/raster-foundry/raster-foundry/pull/5533)
- Added label class and label class group endpoints for campaigns [#5534](https://github.com/raster-foundry/raster-foundry/pull/5534)
- Added a QP for granting requester accesses to project and campaign in the campaign random task GET endpoint [#5539](https://github.com/raster-foundry/raster-foundry/pull/5539)

### Removed
- Remove Nginx configuration and containers for Backsplash [#5532](https://github.com/raster-foundry/raster-foundry/pull/5532)

### Changed
- Annotation projects can always be authorized from their parent campaign [#5536](ttps://github.com/raster-foundry/raster-foundry/pull/5536), [#5537](https://github.com/raster-foundry/raster-foundry/pull/5537)
- Render label MVTs in the tile server instead of in the database [#5538](https://github.com/raster-foundry/raster-foundry/pull/5538)

## [1.57.0] - 2020-12-16
### Added
- Add TaskSession data model and basic endpoints [#5522](https://github.com/raster-foundry/raster-foundry/pull/5522)
- Support fetching random task session [#5525](https://github.com/raster-foundry/raster-foundry/pull/5525)
- Added CRUD endpoints for annotation project label class groups and label classses [#5526](https://github.com/raster-foundry/raster-foundry/pull/5526)
- Added additional fields to the labels MVT endpoint [#5530](https://github.com/raster-foundry/raster-foundry/pull/5530)

## [1.56.0] - 2020-12-04
### Added
- Added rewrite for tile requests prefixed with `/tiles/` [#5527](https://github.com/raster-foundry/raster-foundry/pull/5527)

## [1.55.0] - 2020-12-03
### Added
- Added `POST` to incrementally add labels with labeling and validating [#5518](https://github.com/raster-foundry/raster-foundry/pull/5518)

### Fixed
- Tasks can be unlocked by users other than the user who locked them [#5514](https://github.com/raster-foundry/raster-foundry/pull/5514)
- Made python Upload datamodel aware of campaign ID [#5513](https://github.com/raster-foundry/raster-foundry/pull/5513)
- Fixed advisory lock behavior and also fixed unlocking to skip from statuses that are the same [#5523](https://github.com/raster-foundry/raster-foundry/pull/5523)

### Changed
- Groundwork users can create unlimited annotation projects, but only 10 campaigns [#5516](https://github.com/raster-foundry/raster-foundry/pull/5516)
- Support X-Ray on Fargate [#5519](https://github.com/raster-foundry/raster-foundry/pull/5519)

## [1.54.0] - 2020-11-25
### Added
- Added campaign to limits endpoint [#5511](https://github.com/raster-foundry/raster-foundry/pull/5511)

### Fixed
- Simple shares don't always return 500 responses [#5510](https://github.com/raster-foundry/raster-foundry/pull/5510)
- Upgraded GeoTrellis to remove seams for some zoom + extent + projection combinations [#5515](https://github.com/raster-foundry/raster-foundry/pull/5515)

## [1.53.0] - 2020-11-16
### Added
- Adding labels will now delete all prior labels first [#5512](https://github.com/raster-foundry/raster-foundry/pull/5512)
- STAC label items now include the task statuses in a `groundwork:taskStatuses` property [#5490](https://github.com/raster-foundry/raster-foundry/pull/5490)
- Tasks can be listed by campaign [#5494](https://github.com/raster-foundry/raster-foundry/pull/5494)
- Campaigns can be shared with only an email [#5495](https://github.com/raster-foundry/raster-foundry/pull/5495)
- Campaign IDs can be used to filter STAC exports [#5498](https://github.com/raster-foundry/raster-foundry/pull/5498)
- API support for splitting tasks [#5502](https://github.com/raster-foundry/raster-foundry/pull/5502)

### Changed
- Campaigns are now optionally associated with `AnnotationLabelClassGroup`s and return associated groups from `/campaign` endpoints [#5500](https://github.com/raster-foundry/raster-foundry/pull/5500)
- Campaigns now include image counts and task status summaries [#5501](https://github.com/raster-foundry/raster-foundry/pull/5501)

### Fixed
- Tasks that have `VALIDATION_IN_PROGRESS` or `LABELING_IN_PROGRESS` but no lock data are also expired automatically [#5496](https://github.com/raster-foundry/raster-foundry/pull/5496)
- Backsplash background processes for automatic task unlocks no longe rcompete with each other [#5503](https://github.com/raster-foundry/raster-foundry/pull/5503)

## [1.52.2] - 2020-11-05
### Fixed
- Removed incompatible creation option from cogification command when the image has 64-bit data [#5506](https://github.com/raster-foundry/raster-foundry/pull/5506)

## [1.52.1] - 2020-10-13
### Fixed
- Fixed a URL decoding bug that caused imagery to be unavailable after upload processing [#5492](https://github.com/raster-foundry/raster-foundry/pull/5492)

## [1.52.0] - 2020-10-12
### Changed
- Moved footprint and histogram calculation on scene post into background process [#5487](https://github.com/raster-foundry/raster-foundry/pull/5487)
- STAC exports include signed URLs to tiff assets instead of tiff assets [#5487](https://github.com/raster-foundry/raster-foundry/pull/5487)

## [1.51.0] - 2020-10-02
### Added
- Added `review_status` to tasks, added a DB trigger to update this field when children task reviews change [#5478](https://github.com/raster-foundry/raster-foundry/pull/5478)
- Campaigns can be exported [#5482](https://github.com/raster-foundry/raster-foundry/pull/5482)

### Changed
- Removed spaces from usernames generated by the psuedo username service [#5479](https://github.com/raster-foundry/raster-foundry/pull/5479)
- Moved task grid creation into a background task [#5476](https://github.com/raster-foundry/raster-foundry/pull/5476)
- Hand-rolled cogification commands have been replaced with `gdal_translate -of COG` [#5477](https://github.com/raster-foundry/raster-foundry/pull/5477)
- Annotation projects requested for campaigns use campaign authorization [#5481](https://github.com/raster-foundry/raster-foundry/pull/5481), [#5483](https://github.com/raster-foundry/raster-foundry/pull/5483)

### Removed
- Import processing for everything but user geotiffs [#5477](https://github.com/raster-foundry/raster-foundry/pull/5477)
- Cogification for Landsat 8 and Sentinel-2 imagery [#5477](https://github.com/raster-foundry/raster-foundry/pull/5477)
- Sentinel-2 and Landsat 8 SNS subscription Lambda functions [#5486](https://github.com/raster-foundry/raster-foundry/pull/5486)

## [1.50.0] - 2020-09-21
### Added
- Creating a scene from a COG eagerly fetches GeoTiff information [#5473](https://github.com/raster-foundry/raster-foundry/pull/5473)
- Send notifications at the conclusion of STAC exports [#5470](https://github.com/raster-foundry/raster-foundry/pull/5470)

## [1.49.0] - 2020-09-04
### Added
- Included user IDs in traces [#5464](https://github.com/raster-foundry/raster-foundry/pull/5464)

### Changed
- Failed Intercom API interaction no longer throws an exception [#5468](https://github.com/raster-foundry/raster-foundry/pull/5468)
- Do not render low zoom levels that are blank for imagery [#5472](https://github.com/raster-foundry/raster-foundry/pull/5472)

## [1.48.0] - 2020-08-25
### Added
- Add validation specific endpoint [#5453](https://github.com/raster-foundry/raster-foundry/pull/5453)
- Added three additional categories for usernames when bulk-creating [#5458](https://github.com/raster-foundry/raster-foundry/pull/5458)

### Changed
- Upgrade STAC version for exports [#5456](https://github.com/raster-foundry/raster-foundry/pull/5456)
- Update annotation project update endpoint [#5461](https://github.com/raster-foundry/raster-foundry/pull/5461)
- Update annotation project status DB trigger to listen to delete actions in addition to update [#5461](https://github.com/raster-foundry/raster-foundry/pull/5461)

### Fixed
- Apply single band settings to project default layer upon project create [#5454](https://github.com/raster-foundry/raster-foundry/pull/5454)
- Switched to GDALRasterSource in COG scene posts to fetch metadata without timeouts [#5459](https://github.com/raster-foundry/raster-foundry/pull/5459), [#5460](https://github.com/raster-foundry/raster-foundry/pull/5460)

## [1.47.0] - 2020-07-27
### Changed
- Update annotation project share endpoint for validation use case [#5452](https://github.com/raster-foundry/raster-foundry/pull/5452)

### Fixed
- Fixed a tiff tag parsing bug and allow for disabling color correction via a query parameter [#5455](https://github.com/raster-foundry/raster-foundry/pull/5455)

## [1.46.1] - 2020-07-17
### Fixed
- Fixed the ACR assignment issue on the user bulk creation endpoint [#5450](https://github.com/raster-foundry/raster-foundry/pull/5450)

## [1.46.0] - 2020-07-16
### Fixed
- Fixed tile rendering by updating GeoTrellis to 3.4.1 [#5449](https://github.com/raster-foundry/raster-foundry/pull/5449)

## 1.45.0 - 2020-07-15
### Added
- Add support for campaign resource links [#5445](https://github.com/raster-foundry/raster-foundry/pull/5445)

### Changed
- Failed uploads copy source imagery to S3 if they're in a Raster Foundry user data bucket [#5446](https://github.com/raster-foundry/raster-foundry/pull/5446)
- Improve histogram generation when COGs are POSTed to the scenes endpoint [#5444](https://github.com/raster-foundry/raster-foundry/pull/5444)

### Fixed
- Copied back labels should be associated with label classes [#5448](https://github.com/raster-foundry/raster-foundry/pull/5448)
- `load_development_data --create` should correctly provide a time format parameter [#5448](https://github.com/raster-foundry/raster-foundry/pull/5448)

## [1.44.2] - 2020-07-09
### Fixed
- Filtered locked tasks out of random tasks endpoint [#5442](https://github.com/raster-foundry/raster-foundry/pull/5442)
- Prevented `label_class_history` from blocking annotation project deletion [#5442](https://github.com/raster-foundry/raster-foundry/pull/5442)

## [1.44.1] - 2020-07-08
### Fixed
- Random tasks can only be drawn from sibling projects [#5440](https://github.com/raster-foundry/raster-foundry/pull/5440)

## [1.44.0] - 2020-07-08
### Added
- Campaigns can provide a random reviewable task for users to review [#5435](https://github.com/raster-foundry/raster-foundry/pull/5435)
- Campaigns can copy labels back from their children's annotation projects [#5436](https://github.com/raster-foundry/raster-foundry/pull/5436)

### Fixed
- Fix Maven Central release pipeline hanging behavior [#5438](https://github.com/raster-foundry/raster-foundry/pull/5438)

## [1.43.0] - 2020-06-30
### Added
- Add support for task children [#5427](https://github.com/raster-foundry/raster-foundry/pull/5427)

### Changed
- Upgraded sbt-assembly to 0.15.0 and removed superfluous build.sbt files [#5434](https://github.com/raster-foundry/raster-foundry/pull/5434/files)

## [1.42.0] - 2020-06-25
### Added
- Add endpoint for listing users who own a clone of a specified campaign [#5424](https://github.com/raster-foundry/raster-foundry/pull/5424)
- Enable sorting by `children_count` on `campaigns` table and `captured_at` on `annotation_projects` table [#5416](https://github.com/raster-foundry/raster-foundry/pull/5416)
- Add description field to annotation label [#5420](https://github.com/raster-foundry/raster-foundry/pull/5420)

### Changed
- Task lock expiration now consults previous status, unlocks tasks in any status, and preserves notes [#5414](https://github.com/raster-foundry/raster-foundry/pull/5414), [#5419](https://github.com/raster-foundry/raster-foundry/pull/5419)
- Upgraded to GeoTrellis 3.4 [#5426](https://github.com/raster-foundry/raster-foundry/pull/5426)
- Update campaign clone endpoint and user bulk create endpoint [#5425](https://github.com/raster-foundry/raster-foundry/pull/5425)
- Upgrade to sbt-gpg and GnuPG 2+ [#5423](https://github.com/raster-foundry/raster-foundry/pull/5423)

## [1.41.0] - 2020-05-27
### Added
- Additional statuses for tasks indicating invalid tasks and flagged tasks [#5403](https://github.com/raster-foundry/raster-foundry/pull/5403)
- MVT endpoints for annotation project tasks and labels [#5400](https://github.com/raster-foundry/raster-foundry/pull/5400)
- Support campaigns [#5397](https://github.com/raster-foundry/raster-foundry/pull/5397) [#5404](https://github.com/raster-foundry/raster-foundry/pull/5404) [#5410](https://github.com/raster-foundry/raster-foundry/pull/5410)
- Add support for creating users in bulk [#5406](https://github.com/raster-foundry/raster-foundry/pull/5406)
- Add geometry type and description to Annotation Label Classes [#5408](https://github.com/raster-foundry/raster-foundry/pull/5408)
- Add image quality to tile layers [#5409](https://github.com/raster-foundry/raster-foundry/pull/5409)
- Add campaign children count and status flag [#5412](https://github.com/raster-foundry/raster-foundry/pull/5412)
- Add campaign clone endpoint, add tags to campaigns table [#5413](https://github.com/raster-foundry/raster-foundry/pull/5413)
- Enable archiving campaigns [#5415](https://github.com/raster-foundry/raster-foundry/pull/5415)

### Changed
- STAC exports' files now have friendlier names for humans [#5407](https://github.com/raster-foundry/raster-foundry/pull/5407)
- Changed email invitation copy [#5396](https://github.com/raster-foundry/raster-foundry/pull/5396)
- Add development support for PostgreSQL 12.x/PostGIS 3.x [#5395](https://github.com/raster-foundry/raster-foundry/pull/5395)
- Upgraded GeoTrellis and Tyelevel libraries to Cats 2.x releases [#5393](https://github.com/raster-foundry/raster-foundry/pull/5393), [#5401](https://github.com/raster-foundry/raster-foundry/pull/5401), [#5402](https://github.com/raster-foundry/raster-foundry/pull/5402)
- Relaxed criteria used to determine whether or not to clip and stretch imagery [#5411](https://github.com/raster-foundry/raster-foundry/pull/5411)

### Fixed
- Don't write `self` links to STAC roots [#5405](https://github.com/raster-foundry/raster-foundry/pull/5405)

## [1.40.3] - 2020-04-08
### Fixed
- `LABELED` and `VALIDATED` task locks also automatically expire [#5390](https://github.com/raster-foundry/raster-foundry/pull/5390)

## [1.40.2] - 2020-04-07
### Fixed
- Simplified task status reversion logic in automatic task lock expiration [#5389](https://github.com/raster-foundry/raster-foundry/pull/5389)

## [1.40.1] - 2020-04-06
### Fixed
- Made email to new users who've been shared with prettier, less Star Wars-y [#5388](https://github.com/raster-foundry/raster-foundry/pull/5388)

## [1.40.0] - 2020-04-06
### Added
- Notify Groundwork users when annotation projects are shared with them [#5383](https://github.com/raster-foundry/raster-foundry/pull/5383), [#5386](https://github.com/raster-foundry/raster-foundry/pull/5386)

### Fixed
- Set `lockedBy` and `lockedOn` to null when expiring tasks [#5385](https://github.com/raster-foundry/raster-foundry/pull/5385)

## [1.39.0] - 2020-04-02
### Added
- Added a random task endpoint to get a random task meeting some task query parameters in an annotation project satisfying some annotation project query parameters [#5378](https://github.com/raster-foundry/raster-foundry/pull/5378)
- Add task status filter to annotation projects [#5373](https://github.com/raster-foundry/raster-foundry/pull/5373) [#5379](https://github.com/raster-foundry/raster-foundry/pull/5379) [#5382](https://github.com/raster-foundry/raster-foundry/pull/5382)

### Changed
- STAC exports now include underlying imagery [#5380](https://github.com/raster-foundry/raster-foundry/pull/5380)

### Fixed
- Automatically reset tasks to their previous state if they're stuck in progress for a configurable length of time [#5367](https://github.com/raster-foundry/raster-foundry/pull/5367)

## [1.38.1] - 2020-03-24
### Fixed
- Moved buffering to TMS extents to avoid extreme CPU load resulting from previous buffering strategy [#5375](https://github.com/raster-foundry/raster-foundry/pull/5375)

## [1.38.0] - 2020-03-23
### Added
- Support sample projects for new annotation app users [#5362](https://github.com/raster-foundry/raster-foundry/pull/5362), [#5368](https://github.com/raster-foundry/raster-foundry/pull/5368)

### Changed
- TMS polygons are buffered when querying for scenes to put a band aid on errors in footprint calculation in upload processing [#5374](https://github.com/raster-foundry/raster-foundry/pull/5374)

## [1.37.0] - 2020-03-18
### Added
- Notify users with Intercom when annotation project processing has completed [#5355](https://github.com/raster-foundry/raster-foundry/pull/5355), [#5361](https://github.com/raster-foundry/raster-foundry/pull/5361)

## [1.36.0] - 2020-03-12
### Fixed
- Fixed an issues with upload deserialization [#5356](https://github.com/raster-foundry/raster-foundry/pull/5356)

## [1.35.0] - 2020-03-10
### Added
- Deleting annotation projects deletes all related data (scenes, uploads, uploaded data, projects) [#5351](https://github.com/raster-foundry/raster-foundry/pull/5351)
- Update annotation projects after upload processing as appropriate [#5324](https://github.com/raster-foundry/raster-foundry/pull/5324), [#5334](https://github.com/raster-foundry/raster-foundry/pull/5334)
- Added special share endpoints that can be used with just an email for annotation projects [#5321](https://github.com/raster-foundry/raster-foundry/pull/5321), [#5327](https://github.com/raster-foundry/raster-foundry/pull/5327), [#5336](https://github.com/raster-foundry/raster-foundry/pull/5336), [#5338](https://github.com/raster-foundry/raster-foundry/pull/5338)
- Added a CSV-configurable scope checking integration test [#5297](https://github.com/raster-foundry/raster-foundry/pull/5297), [#5306](https://github.com/raster-foundry/raster-foundry/pull/5306)
- Added POST endpoint for annotation projects and their related fields [#5294](https://github.com/raster-foundry/raster-foundry/pull/5294)
- Added `signed-url` endpoint for uploads to send secure PUTs to s3 [#5290](https://github.com/raster-foundry/raster-foundry/pull/5290)
- Added migration for annotate projects and related items [#5284](https://github.com/raster-foundry/raster-foundry/pull/5284)
- Added scopes to the API, database, and user creation to control access to resources and endpoints [#5270](https://github.com/raster-foundry/raster-foundry/pull/5270), [#5275](https://github.com/raster-foundry/raster-foundry/pull/5275), [#5278](https://github.com/raster-foundry/raster-foundry/pull/5278) [#5277](https://github.com/raster-foundry/raster-foundry/pull/5277), [#5292](https://github.com/raster-foundry/raster-foundry/pull/5292), [#5346](https://github.com/raster-foundry/raster-foundry/pull/5346)
- Generate typescript interfaces in CI [#5271](https://github.com/raster-foundry/raster-foundry/pull/5271)
- Added a migration to add annotation project id to tasks [#5296](https://github.com/raster-foundry/raster-foundry/pull/5296)
- Added annotation project related data models,   `Dao` methods, endpoints, and tests [#5301](https://github.com/raster-foundry/raster-foundry/pull/5301), [#5303](https://github.com/raster-foundry/raster-foundry/pull/5303), [#5308](https://github.com/raster-foundry/raster-foundry/pull/5308), [#5318](https://github.com/raster-foundry/raster-foundry/pull/5318)
- Add /user/me/limits endpoint for listing how many projects, shared, and how much upload space a user is using [#5320](https://github.com/raster-foundry/raster-foundry/pull/5320)

### Changed
- Altered permission resolution to prefer scoped actions with higher limits [#5316](https://github.com/raster-foundry/raster-foundry/pull/5316/files)
- Improved error handling in Groundwork project migration [#5310](https://github.com/raster-foundry/raster-foundry/pull/5310)
- Logout can redirect to a custom URL [#5291](https://github.com/raster-foundry/raster-foundry/pull/5291)
- Moved project data for Groundwork from extras jsonb field and a text field to normalized tables [#5286](https://github.com/raster-foundry/raster-foundry/pull/5286)
- Clear Python Lambda function pip cache [#5274](https://github.com/raster-foundry/raster-foundry/pull/5274)
- Updated to support STAC exports on annotation projects [#5312](https://github.com/raster-foundry/raster-foundry/pull/5312) [#5323](https://github.com/raster-foundry/raster-foundry/pull/5323)
- Made permission replacement obey same scope rules [#5343](https://github.com/raster-foundry/raster-foundry/pull/5343)
- Updated the task grid creation SQL function to clip task cells to project footprint [#5344](https://github.com/raster-foundry/raster-foundry/pull/5344)
- Change the `ready` boolean field to a `status` enum field for better descriptions of processing failures [#5350](https://github.com/raster-foundry/raster-foundry/pull/5350)

### Fixed
- Upgrade pyproj to make app-tasks python3.7 compatible [#5352](https://github.com/raster-foundry/raster-foundry/pull/5352)
- Upload updates now respect all fields [#5330](https://github.com/raster-foundry/raster-foundry/pull/5330)
- Removed unused route and fixed spelling in Swagger spec [#5273](https://github.com/raster-foundry/raster-foundry/pull/5273)
- Fix 403 when users with no uploads try to create an upload [#5337](https://github.com/raster-foundry/raster-foundry/pull/5337)

## [1.34.1] - 2020-01-28
### Changed
- Logout can redirect to a custom URL [#5291](https://github.com/raster-foundry/raster-foundry/pull/5291)

## [1.34.0] - 2019-12-24
### Added
- Support semantic segmentation stac exports [#5253](https://github.com/raster-foundry/raster-foundry/pull/5253)
- Added default clipping of non-byte rasters to middle 96 percent of values assuming a normal distribution [#5264](https://github.com/raster-foundry/raster-foundry/pull/5264)

### Fixed
- Unified file extension for label item assets and label item asset links [#5252](https://github.com/raster-foundry/raster-foundry/pull/5252)
- Added index to annotations `task_id` column to make deleting tasks way faster [#5255](https://github.com/raster-foundry/raster-foundry/pull/5255)
- Explicitly handled case where aggregation of tasks doesn't return geometry statistics in STAC export [#5256](https://github.com/raster-foundry/raster-foundry/pull/5256)
- Fixed upload processing bug that caused all scenes with simple footprints to fail upload processing [#5268](https://github.com/raster-foundry/raster-foundry/pull/5268)

## [1.33.0] - 2019-12-03
### Removed
- CMR and Shapefile support [#5247](https://github.com/raster-foundry/raster-foundry/pull/5247)
- Complex color correction (saturation, per-band clipping, contrast adjustment, etc.) [#5245](https://github.com/raster-foundry/raster-foundry/pull/5245)

### Fixed
- Fixed usage of license types for STAC exports [#5240](https://github.com/raster-foundry/raster-foundry/pull/5240)
- Fix metadata backfill batch job [#5249](https://github.com/raster-foundry/raster-foundry/pull/5249)

## [1.32.1] - 2019-10-29
### Fixed
- Fixed bug listing task summaries [#5238](https://github.com/raster-foundry/raster-foundry/pull/5238)

## [1.32.0] - 2019-10-28
### Added
- Included TaskARN in tile server traces [#5201](https://github.com/raster-foundry/raster-foundry/pull/5201)
- Add zipped stac export url to stac objects returned from API [#5220](https://github.com/raster-foundry/raster-foundry/pull/5220)
- Add Geojson Upload datamodel and api [#5223](https://github.com/raster-foundry/raster-foundry/pull/5223)
- Add Geojson Upload batch job [#5230](https://github.com/raster-foundry/raster-foundry/pull/5230)

### Changed
- Changed serialization of scenes to layer cache to binary [#5218](https://github.com/raster-foundry/raster-foundry/pull/5218)
- Upgraded STAC version in STAC export builder [#5202](https://github.com/raster-foundry/raster-foundry/pull/5202)
- Upgrade http4s to 0.20.11 [#5213](https://github.com/raster-foundry/raster-foundry/)
- Started publishing `http4s-util` artifact [#5224](https://github.com/raster-foundry/raster-foundry/pull/5224)
- Added custom raster source to use cached GeoTiffInfo for serving tiles [#5222](https://github.com/raster-foundry/raster-foundry/pull/5222)

### Removed
- Remove AOI project creation from frontend [#5228](https://github.com/raster-foundry/raster-foundry/pull/5228)
- Removed blog/feed endpoint and frontend [#5216](https://github.com/raster-foundry/raster-foundry/pull/5216)
- Removed project overview generation [#5222](https://github.com/raster-foundry/raster-foundry/pull/5222)

### Fixed
- STAC bug fixes: Stopped serializing null label extension fields on STAC label items, stopped URL encoding absolute links, fixed failure of STAC export if no task statuses selected [#5227](https://github.com/raster-foundry/raster-foundry/pull/5227)
- Used item IDs instead of `"item.json"` for STAC item names [#5226](https://github.com/raster-foundry/raster-foundry/pull/5226)
- Fixed a routing bug that prevented viewing tiles under the `/scenes/` routes [#5213](https://github.com/raster-foundry/raster-foundry/)

## [1.31.0] - 2019-09-30
### Added
- Added caching of mosaic definition queryes [#5192](https://github.com/raster-foundry/raster-foundry/pull/5192)

### Changed
- Optimize long-running task query [#5187](https://github.com/raster-foundry/raster-foundry/pull/5187)
- Use default execution contexts for http4s and blaze and switch to using an unbounded pool for doobie transactions [#5188](https://github.com/raster-foundry/raster-foundry/pull/5188)

### Removed
- Remove broken RasterSource and Tile caches [#5190](https://github.com/raster-foundry/raster-foundry/pull/5190)

## [1.30.0] - 2020-09-19
### Added
- Added (feature flagged) support for visualizing Landsat 8 scenes directly from AWS without ingest [#5167](https://github.com/raster-foundry/raster-foundry/pull/5167), currently ineffective support for multitiff imagery for Landsats 4 / 5 / 7 and Sentinel-2 [#5178](https://github.com/raster-foundry/raster-foundry/pull/5178)
- Expose scene id in the scene detail modal [#5168](https://github.com/raster-foundry/raster-foundry/pull/5168)
- Added tracing support to tile server [#5165](https://github.com/raster-foundry/raster-foundry/pull/5165)[#5171](https://github.com/raster-foundry/raster-foundry/pull/5171)

### Changed
- Improve histogram generation by using RasterSources [#5169](https://github.com/raster-foundry/raster-foundry/pull/5169)
- Adjusted the healthcheck to more easily distinguish between errors and timeouts [#5179](https://github.com/raster-foundry/raster-foundry/pull/5179)

### Removed
- Remove API spec publishing [#5174](https://github.com/raster-foundry/raster-foundry/pull/5174)

### Fixed
- Remove unnecessary coercion of scenes to a nodata value of 0 in single band mode [#5173](https://github.com/raster-foundry/raster-foundry/pull/5173)
- Made MODIS Aqua datasource id in the frontend a valid UUID [#5175](https://github.com/raster-foundry/raster-foundry/pull/5175)

## [1.29.1] - 2019-09-12
### Fixed
- Changed database log level to debug [#5164](https://github.com/raster-foundry/raster-foundry/pull/5164)
- Used absolute S3 link for STAC label data upload [#5164](https://github.com/raster-foundry/raster-foundry/pull/5164)

## [1.29.0] - 2019-09-11
### Added
- Added trace ID to exception logging for backsplash [#5134](https://github.com/raster-foundry/raster-foundry/pull/5134)
- Added projectId and layerId QPs to STAC export list endpoint [#5140](https://github.com/raster-foundry/raster-foundry/pull/5140)
- Added caching for selecting projects, project layers, and users by ID [#5144](https://github.com/raster-foundry/raster-foundry/pull/5144)
- Added documentation on choice of tracing libraries [#5145](https://github.com/raster-foundry/raster-foundry/pull/5145)
- Added a check for band counts during project exports [#5154](https://github.com/raster-foundry/raster-foundry/pull/5154)
- Added a query parameter to list tasks union by status [#5159](https://github.com/raster-foundry/raster-foundry/pull/5159)

### Changed
- Upgraded doobie to 0.7.0 [#5130](https://github.com/raster-foundry/raster-foundry/pull/5130)
- Updated STAC export location to use the S3 prefix [#5140](https://github.com/raster-foundry/raster-foundry/pull/5140)
- Updated values for label:task, label:property, and label:classes of the STAC label item [#5140](https://github.com/raster-foundry/raster-foundry/pull/5140)
- Tuned proxy_connect_timeout to make Nginx fail faster [#5133](https://github.com/raster-foundry/raster-foundry/pull/5133)
- Change absolute links in stac export to relative, so they make sense in a local downloaded context [#5155](https://github.com/raster-foundry/raster-foundry/pull/5155), [#5161](https://github.com/raster-foundry/raster-foundry/pull/5161)

### Fixed
- Repaired python scene codec for API interaction in scene ingests [#5148](https://github.com/raster-foundry/raster-foundry/pull/5148)
- Eliminated superfluous scene queries in thumbnail rendering [#5139](https://github.com/raster-foundry/raster-foundry/pull/5139)
- Fixed chip classification STAC data export and scene item `datetime` field for both project types [#5158](https://github.com/raster-foundry/raster-foundry/pull/5158)

## [1.28.0] - 2019-08-26
### Added
- Added an ADR for label STAC export [#5127](https://github.com/raster-foundry/raster-foundry/pull/5127)

### Fixed
- Better error messages / codes when rendering tiles containing scenes which are missing histograms [/5128](https://github.com/raster-foundry/raster-foundry/pull/5128)
- Correctly mosaic tiles which have a no-data value other than 0 [#5131](https://github.com/raster-foundry/raster-foundry/pull/5131)

## [1.27.0] - 2019-08-21
### Added
- Added STAC async export endpoints and export builder in batch [#5018](https://github.com/raster-foundry/raster-foundry/pull/5018)
- Enabled writing STAC exports to S3 [#5110](https://github.com/raster-foundry/raster-foundry/pull/5110)
- Enabled kicking off STAC export when an export record is created [#5116](https://github.com/raster-foundry/raster-foundry/pull/5116)

### Fixed
- Optimized long-running annotation group summary endpoint [/#5155](https://github.com/raster-foundry/raster-foundry/pull/5155)

## [1.26.0] - 2019-08-12
### Added
- Added `taskId` to annotations and corresponding query paramter [#5073](https://github.com/raster-foundry/raster-foundry/pull/5073)
- Added fields to track metadata for scenes to help with RasterSource construction [#5103](https://github.com/raster-foundry/raster-foundry/pull/5103)

### Changed
- Add support for multiband imagery in WMS responses
  [#5082](https://github.com/raster-foundry/raster-foundry/pull/5082)

### Fixed
- Fixed missing logs from scala services [#5094](https://github.com/raster-foundry/raster-foundry/pull/5094)

## [1.25.1] - 2019-07-31
### Fixed
- Fixed missing logs from scala services [#5094](https://github.com/raster-foundry/raster-foundry/pull/5094)

## [1.25.0] - 2019-07-18
### Added
- Capture X-Amzn-Trace-Id in Nginx access logs [#5068](https://github.com/raster-foundry/raster-foundry/pull/5068)

### Changed
- Upgraded version of GeoTrellis Server and MAML [#5046](https://github.com/raster-foundry/raster-foundry/pull/5046), [#5063](https://github.com/raster-foundry/raster-foundry/pull/5063)
- Updated user task activity endpoint [#5078](https://github.com/raster-foundry/raster-foundry/pull/5078)

### Fixed
- Better error handling for ingests, no longer always fail the batch job [/#5070](https://github.com/raster-foundry/raster-foundry/pull/5070)

## [1.24.0] - 2019-07-09
### Added
- Added an endpoint for listing user activity summary on tasks [#5051](https://github.com/raster-foundry/raster-foundry/pull/5051)

### Fixed
- Removed superfluous parameter from `POST`s to `/annotation-groups` endpoints [#5041](https://github.com/raster-foundry/raster-foundry/pull/5041)

## [1.23.0] - 2019-07-03
### Added
- Limited max zoom level for backsplash tiles to 23 on frontend [#5013](https://github.com/raster-foundry/raster-foundry/pull/5013)

### Fixed
- Fixed bugs in task list and delete endpoints [#5026](https://github.com/raster-foundry/raster-foundry/pull/5026) [#5034](https://github.com/raster-foundry/raster-foundry/pull/5034) [#5042](https://github.com/raster-foundry/raster-foundry/pull/5042) [#5038](https://github.com/raster-foundry/raster-foundry/pull/5038) [#5048](https://github.com/raster-foundry/raster-foundry/pull/5048)

## [1.22.0] - 2019-06-11
### Added
- Added `ActionType` for `CREATE` [#4992](https://github.com/raster-foundry/raster-foundry/pull/4992)
- Added `ObjectType` for all entities [#4992](https://github.com/raster-foundry/raster-foundry/pull/4992)
- Added job to backfill low-zoom overviews for existing project layers [#4970](https://github.com/raster-foundry/raster-foundry/pull/4970)
- Convert BacksplashImage to a trait [#4952](https://github.com/raster-foundry/raster-foundry/pull/4952)
- Kickoff project layer overview generation reactively [#4936](https://github.com/raster-foundry/raster-foundry/pull/4936)
- Added ability to persist container service core dumps [#4955](https://github.com/raster-foundry/raster-foundry/pull/4955)
- Added database support for tasks on projects + project layers [#4996](https://github.com/raster-foundry/raster-foundry/pull/4996)
- Support storing processed uploads in source bucket [#4997](https://github.com/raster-foundry/raster-foundry/pull/4997)
- Add spec for user scopes CRUD [#5002](https://github.com/raster-foundry/raster-foundry/pull/5002)
- Use zoom level in OverviewInput for lambda function to generate layer overviews [#4998](https://github.com/raster-foundry/raster-foundry/pull/4998)
- Added endpoints for project layer "tasks" [#5008](https://github.com/raster-foundry/raster-foundry/pull/5008)
- Add user scope migration and data model [#5009](https://github.com/raster-foundry/raster-foundry/pull/5009)
- Updated healthcheck to include loading tile metadata [#5017](https://github.com/raster-foundry/raster-foundry/pull/5017)
- Added task grid endpoint [#5021](https://github.com/raster-foundry/raster-foundry/pull/5021)
- Made tile server report that it's sick after it serves a number of requests [#5024](https://github.com/raster-foundry/raster-foundry/pull/5024)

### Changed
- Use flyway for running migrations and publish image for flyway migrations [#4987](https://github.com/raster-foundry/raster-foundry/pull/4987)
- Added support for uploading non-spatial data [#4993](https://github.com/raster-foundry/raster-foundry/pull/4993)[#5001](https://github.com/raster-foundry/raster-foundry/pull/5001)

### Fixed
- Fixed usage of AWS credentials by the GDAL java bindings by using `AWS_DEFAULT_PROFILE` [#4948](https://github.com/raster-foundry/raster-foundry/pull/4948)
- Fix Sentinel-2 metadata file download [#4969](https://github.com/raster-foundry/raster-foundry/pull/4969)
- Fix scripts/update so that it uses flyway for migrations [#5032](https://github.com/raster-foundry/raster-foundry/pull/5032)

## [1.21.3] - 2019-06-06
### Added
- Updated healthcheck to include loading tile metadata [#5017](https://github.com/raster-foundry/raster-foundry/pull/5017)

## [1.21.2] - 2019-05-23
### Added
- Convert BacksplashImage to a trait [#4952](https://github.com/raster-foundry/raster-foundry/pull/4952)

### Fixed
- Fixed logging configuration to respect `RF_LOG_LEVEL` environment variable [#4972](https://github.com/raster-foundry/raster-foundry/pull/4972)
- Fixed a bug causing all raster sources to share a cache location [#5003](https://github.com/raster-foundry/raster-foundry/pull/5003)

## [1.21.1] - 2019-05-15
### Fixed
- Stopped returning internal server errors when requesting tiles outside a project boundary [#4956](https://github.com/raster-foundry/raster-foundry/pull/4956)

## [1.21.0] - 2019-05-09
### Added
- Add lambda function for creating project layer overviews and reorganize project [#4900](https://github.com/raster-foundry/raster-foundry/pull/4900)
- Add owner name and profile URL to the return of annotation GET endpoint [#4924](https://github.com/raster-foundry/raster-foundry/pull/4924)
- Added request counting middleware to backsplash-server [#4919](https://github.com/raster-foundry/raster-foundry/pull/4919)
- Enabled getting and writing user group roles from JWT [#4931](https://github.com/raster-foundry/raster-foundry/pull/4931)
- Added check for pre-generated overview tif for low zoom levels [#4942](https://github.com/raster-foundry/raster-foundry/pull/4942)

### Fixed
- Fixed query parameter handling that listed all tool run map tokens with project map tokens [#4928](https://github.com/raster-foundry/raster-foundry/pull/4928)
- Fixed a bug that caused tile server interaction with the cache to be unsuccessful for reads and writes [#4939](https://github.com/raster-foundry/raster-foundry/pull/4939)
- Fixed compiler warnings triggered by scala 2.12 upgrade and enable fatal warning flag to fail compilation if a compiler warning or scapegoat warning is triggered [#4929](https://github.com/raster-foundry/raster-foundry/pull/4929)

## [1.20.0] - 2019-05-01
### Added
- Included tests for project layer split behavior [#4901](https://github.com/raster-foundry/raster-foundry/pull/4901)
- New publish page now lists analyses for selected layers [\\4902](https://github.com/raster-foundry/raster-foundry/pull/4902)
- Created metrics table and MetricDao for incrementing request counts [#4916](https://github.com/raster-foundry/raster-foundry/pull/4916)

### Changed
- Improved supported CRS for WMS and WCS Services [#4875](https://github.com/raster-foundry/raster-foundry/pull/4875)
- Switched general single band options out for configured single band options from RF database [#4888](https://github.com/raster-foundry/raster-foundry/pull/4888)
- Add gitSnapshots prefix to Maven Central release command [#4874](https://github.com/raster-foundry/raster-foundry/pull/4874)
- Deduplicate datasources returned from layer datasource endpoint [#4885](https://github.com/raster-foundry/raster-foundry/pull/4885)
- Fixed broken publishing workflow when a user owns too many map tokens [#4886](https://github.com/raster-foundry/raster-foundry/pull/4886)
- Void tile cache when project layer mask changes [#4889](https://github.com/raster-foundry/raster-foundry/pulls)
- Cascade layer deletion so that exports are deleted [#4890](https://github.com/raster-foundry/raster-foundry/pull/4890)
- Filtered out MODIS and Landsat 7 images from less than 24 hours ago when browsing NASA CMR [#4896](https://github.com/raster-foundry/raster-foundry/pull/4896)
- Fix anti-meridian splitting logic also splitting prime-meridian scenes [#4904](https://github.com/raster-foundry/raster-foundry/pull/4904)
- Fix prime meridian scenes which were split accidentally [#4921](https://github.com/raster-foundry/raster-foundry/pull/4921)

## [1.19.0] - 2019-04-16
### Added
- OGC
  - Added GetCapabilities WMS and WCS endpoints for projects [#4767](https://github.com/raster-foundry/raster-foundry/pull/4767)
  - Added parameter handling for DescribeCoverage, GetCoverage, and GetMap requests [#4782](https://github.com/raster-foundry/raster-foundry/pull/4782)
  - Added map token authentication to OGC services [#4778](https://github.com/raster-foundry/raster-foundry/pull/4778)
  - Added style creation from bands and color composites on datasources [#4789](https://github.com/raster-foundry/raster-foundry/pull/4789)
- Added backend support for rendering tiles and fetching histograms for analyses with project layers at their leaves [#4603](https://github.com/raster-foundry/raster-foundry/pull/4603)
- Added scene counts to project layer items [#4625](https://github.com/raster-foundry/raster-foundry/pull/4625)
- Added project layer analyses list view [#4585](https://github.com/raster-foundry/raster-foundry/pull/4585)
- Added project layer analyses create view [#4659](https://github.com/raster-foundry/raster-foundry/pull/4659)
- Added backend support for project layer async exports [#4619](https://github.com/raster-foundry/raster-foundry/pull/4619)
- Added front-end support for importing to project layers [#4646](https://github.com/raster-foundry/raster-foundry/pull/4646)
- Enhanced project layer lists to support list access with map tokens and to public projects [#4656](https://github.com/raster-foundry/raster-foundry/pull/4656)
- Added project layer export list UI [#4663](https://github.com/raster-foundry/raster-foundry/pull/4663)
- Added layer ID query parameter to export list endpoint [#4663](https://github.com/raster-foundry/raster-foundry/pull/4663)
- Added project layer annotation UI support [#4665](https://github.com/raster-foundry/raster-foundry/pull/4665)
- Added project settings pages for v2 UI [#4637](https://github.com/raster-foundry/raster-foundry/pull/4637)
- Added project layer color-mode UI [#4706](https://github.com/raster-foundry/raster-foundry/pull/4706)
- Added project layer color-correction UI [#4722](https://github.com/raster-foundry/raster-foundry/pull/4722)
- Added project embed UI [#4793](https://github.com/raster-foundry/raster-foundry/pull/4793/files)
- Added single-band color-mode support to project layer color-mode UI [#4728](https://github.com/raster-foundry/raster-foundry/pull/4728)
- Added owner query parameter to tools and tool-runs endpoints, support multiple owner qp's on applicable endpoints [#4689](https://github.com/raster-foundry/raster-foundry/pull/4689)
- Added Rollbar error reporting to backsplash [#4691](https://github.com/raster-foundry/raster-foundry/pull/4691)
- Added PLATFORM_USERS webpack overrides variable and make default platform filter use those ids [#4692](https://github.com/raster-foundry/raster-foundry/pull/4692)
- Added flag for whether tools can sensibly be run with only a single layer as input [#4701](https://github.com/raster-foundry/raster-foundry/pull/4701) and used it to filter templates for layer analysis creation [#4711](https://github.com/raster-foundry/raster-foundry/pull/4711)
- Added AOI creation UI and component [#4702](https://github.com/raster-foundry/raster-foundry/pull/4702)
- Added single band options to project layers [#4712](https://github.com/raster-foundry/raster-foundry/pull/4712)
- Added project layer export creation UI [#4718](https://github.com/raster-foundry/raster-foundry/pull/4718)
- Enforced project layer AOI existence when browsing for scenes [#4724](https://github.com/raster-foundry/raster-foundry/pull/4724)
- Made templates editable except for their formulas [#4729](https://github.com/raster-foundry/raster-foundry/pull/4729)
- Added project analyses edit modal for v2 UI [#4709](https://github.com/raster-foundry/raster-foundry/pull/4709)
- Added query parameter to limit scene search by layer AOI and updated filters on the frontend [#4733](https://github.com/raster-foundry/raster-foundry/pull/4733)
- Added endpoint for splitting layers up by date and datasource [#4738](https://github.com/raster-foundry/raster-foundry/pull/4738)
- Added the map part of analysis data visualization UI [#4739](https://github.com/raster-foundry/raster-foundry/pull/4739)
- Enabled project layer selection in lab input raster nodes [#4732](https://github.com/raster-foundry/raster-foundry/pull/4732)
- Enabled project layer splitting on frontend [#4766](https://github.com/raster-foundry/raster-foundry/pull/4766)
- Added the histogram part of analysis data visualization UI [#4756](https://github.com/raster-foundry/raster-foundry/pull/4756)
- Add v2 project share page with layers and analyses [#4768](https://github.com/raster-foundry/raster-foundry/pull/4768)
- Added support for listing annotations on public projects without auth or with a map token query parameter [#4795](https://github.com/raster-foundry/raster-foundry/pull/4795)
- Preserve state of visible layers [#4802](https://github.com/raster-foundry/raster-foundry/pull/4802)
- Display processing imports on layer scenes UI [#4809](https://github.com/raster-foundry/raster-foundry/pull/4809)
- Added quick edit functionality for project analyses [#4804](https://github.com/raster-foundry/raster-foundry/pull/4804)
- Add zooming, showing, hiding options to multi-select menu on analyses, layers [\\4816](https://github.com/raster-foundry/raster-foundry/pull/4816)
- Added API specifications back to core repository [#4819](https://github.com/raster-foundry/raster-foundry/pull/4819)
- Added tile server support for visualizing masked mosaic layers [#4822](https://github.com/raster-foundry/raster-foundry/pull/4822/)
- Added `overviewsLocation` and `minZoomLevel` to `projectLayers` [#4857](https://github.com/raster-foundry/raster-foundry/pull/4857)
- Enable listing annotations with owner info [#4864](https://github.com/raster-foundry/raster-foundry/pull/4864)

### Changed
- Updated default project layer color group hex code [#4616](https://github.com/raster-foundry/raster-foundry/pull/4616)
- Updated `gdal-js` and `requests` [#4618](https://github.com/raster-foundry/raster-foundry/pull/4618)
- Swap trash icon for "Remove" text on scene item components [#4621](https://github.com/raster-foundry/raster-foundry/pull/4621)
- Bumped Nginx buffer size for scene creation requests [#4672](https://github.com/raster-foundry/raster-foundry/pull/4672)
- Use layer geometry to add a mask when creating analyses [#4694](https://github.com/raster-foundry/raster-foundry/pull/4694)
- Made tools reference licenses by id [#4701](https://github.com/raster-foundry/raster-foundry/pull/4701)
- Support map tokens on /projects/{} get route, /tool-runs/ get route [#4768](https://github.com/raster-foundry/raster-foundry/pull/4768)
- Enabled inserting annotations in bulk in one `INSERT INTO` command [#4777](https://github.com/raster-foundry/raster-foundry/pull/4777)
- Started using swaggerhub for documentation [#4818](https://github.com/raster-foundry/raster-foundry/pull/4818)
- Made various UI improvements [#4801](https://github.com/raster-foundry/raster-foundry/pull/4801)
- Make `./scripts/console sbt` run with docker dependencies [#4865](https://github.com/raster-foundry/raster-foundry/pull/4865)
- Improved responses and response types for errors in analysis rendering pipeline [#4843](https://github.com/raster-foundry/raster-foundry/pull/4843)
- Make `./scripts/console sbt` run with docker dependencies [#4865](https://github.com/raster-foundry/raster-foundry/pull/4865)

### Removed
- Removed layer re-ordering, layer sorting, layer type selection from UI [#4616](https://github.com/raster-foundry/raster-foundry/pull/4616)
- Removed lingering database assocation of scenes and projects (in deference to scenes to layers to projects) [#4764](https://github.com/raster-foundry/raster-foundry/pull/4764) [#4867](https://github.com/raster-foundry/raster-foundry/pull/4867)
- Removed tool categories and tags [#4779](https://github.com/raster-foundry/raster-foundry/pull/4779)
- Removed unused dependencies and cleaned up the build file [#4870](https://github.com/raster-foundry/raster-foundry/pull/4870)

### Fixed
- No longer exclude public objects from searches for shared objects, except for scenes [#4754](https://github.com/raster-foundry/raster-foundry/pull/4754)
- Users that have edit permissions on an analysis can now share the analysis from within the lab interface [#4797](https://github.com/raster-foundry/raster-foundry/pull/4797)
- Fixed dependency conflict for circe between geotrellis-server, maml, and Raster Foundry [#4703](https://github.com/raster-foundry/raster-foundry/pull/4703)
- Attached ACL policy to exports uploaded to external buckets to allow owner control [#4825](https://github.com/raster-foundry/raster-foundry/pull/4825)
- Restored footprint splitting logic for Landsat 8 and Sentinel-2 import [#4828](https://github.com/raster-foundry/raster-foundry/pull/4828)
- Fix annotation shapefile import and export [#4829](https://github.com/raster-foundry/raster-foundry/pull/4829)
- Set layerId to null when deleting upload records [#4844](https://github.com/raster-foundry/raster-foundry/pull/4844)
- Project analyses Visualize view conditionally overwrites render def instead of always [#4848](https://github.com/raster-foundry/raster-foundry/pull/4848)
- Moved legacy filters over to new styling [#4855](https://github.com/raster-foundry/raster-foundry/pull/4848)
- Make flake8 and pytest ignore dependencies in `opt` directory of `app-lambda` [#4853](https://github.com/raster-foundry/raster-foundry/pull/4853) [#4863](https://github.com/raster-foundry/raster-foundry/pull/4863)
- Fix v2 share page using the wrong endpoint to fetch analyses, fix error states [#4845](https://github.com/raster-foundry/raster-foundry/pull/4845)

## [1.18.1] - 2019-02-20
### Fixed
- Fixed an export bug that caused export failures when using more than one band from the same project [#4636](https://github.com/raster-foundry/raster-foundry/pull/4636)
- Unified coloring for scene detail modal scene preview and map scene preview [#4630](https://github.com/raster-foundry/raster-foundry/pull/4630)
- Moved scene thumbnail endpoint to the tile server for consistent rendering with previews [#4651](https://github.com/raster-foundry/raster-foundry/pull/4651)

## [1.18.0] - 2019-02-11
### Added
- Added new export utility as replacement for the old (non-functioning) spark-based export [#4589](https://github.com/raster-foundry/raster-foundry/pull/4589)
- Added project layer creation workflow's modal on UI [#4575](https://github.com/raster-foundry/raster-foundry/pull/4575)
- Added project layer Annotation related endpoints [#4569](https://github.com/raster-foundry/raster-foundry/pull/4569)
- CRUDL endpoints for project layer scenes [#4550](https://github.com/raster-foundry/raster-foundry/pull/4550)
- Added publishing of lambda functions to CI build process [#4586](https://github.com/raster-foundry/raster-foundry/pull/4586)
- Added tile server support for masked analyses [#4571](https://github.com/raster-foundry/raster-foundry/pull/4571)
- Added project layer navigation bar [#4581](https://github.com/raster-foundry/raster-foundry/pull/4581)
- Added layer parameter to /api/scenes and added inLayer property to scene browse responses [#4615](https://github.com/raster-foundry/raster-foundry/pull/4615)

### Fixed
- Removed unused imports and assignments [#4579](https://github.com/raster-foundry/raster-foundry/pull/4579)
- Included geometry filter in backsplash scene service to prevent erroneous 500s [#4580](https://github.com/raster-foundry/raster-foundry/pull/4580)
- Made scapegoat less angry [#4611](https://github.com/raster-foundry/raster-foundry/pull/4611)
- Set hasNext correctly on /api/scenes when there are more than 100 scenes [#4615](https://github.com/raster-foundry/raster-foundry/pull/4615)
- Use sane default when the accepted query parameter is not set on /api/project/{}/layer/{}/scenes [#4615](https://github.com/raster-foundry/raster-foundry/pull/4615)

## [1.17.1] - 2019-02-04
### Fixed
- Removed references from old tileserver from Jenkinsfile.release

## [1.17.0] - 2019-02-04
### Added
- Templates can now be shared and filtered by ownership [#4357](https://github.com/raster-foundry/raster-foundry/pull/4357)
- Added ProjectLayer datamodel, dao, and migration [#4460](https://github.com/raster-foundry/raster-foundry/pull/4460)
- Added lambda function for reactively processing new Landsat 8 imagery [#4471](https://github.com/raster-foundry/raster-foundry/pull/4471)
- Added lambda function for reactively processing new Sentinel-2 imagery [#4491](https://github.com/raster-foundry/raster-foundry/pull/4491)
- Added a migration that creates relationship among projects, project layers, and scenes and populates corresponding tables. Updated associated data models and `Dao`s [#4479](https://github.com/raster-foundry/raster-foundry/pull/4479)
- CRUDL endpoints for project layers [#4512](https://github.com/raster-foundry/raster-foundry/pull/4512)
- Added project layer mosaic and scene order endpoint [#4547](https://github.com/raster-foundry/raster-foundry/pull/4547)
- Added `SceneToLayer` data model and `SceneToLayerDao`; updated related function calls in `ProjectDao` and project api [#4513](https://github.com/raster-foundry/raster-foundry/pull/4513)
- Added a migration that creates relationship among projects, project layers, and scenes and populates corresponding tables. Updated associated data models and `Dao`s. [#4479](https://github.com/raster-foundry/raster-foundry/pull/4479)
- Allow sharing most objects when you have edit permissions granted to you [#4514](https://github.com/raster-foundry/raster-foundry/pull/4514)
- Added TMS route for project layers [#4523](https://github.com/raster-foundry/raster-foundry/pull/4523)
- Added TMS, quick export, and histogram routes for project layers [#4523](https://github.com/raster-foundry/raster-foundry/pull/4523), [#4553](https://github.com/raster-foundry/raster-foundry/pull/4553)
- Added project, project layer, and template ID fields to tool runs for later filtering [#4546](https://github.com/raster-foundry/raster-foundry/pull/4546) and to API routes as filter fields [#4551](https://github.com/raster-foundry/raster-foundry/pull/4551)
- Added project layer mosaic and scene order endpoint [#4547](https://github.com/raster-foundry/raster-foundry/pull/4547)
- Add Layer ID to Annotations and Annotation Groups [#4558](https://github.com/raster-foundry/raster-foundry/pull/4558)
- Support uploads to project layers on the API [#\\4524](https://github.com/raster-foundry/raster-foundry/pull/4524)

### Changed
- Reorganized project structure to simplify dependency graph (`tool` was mostly removed;   `tool`s still-relevant pieces,   `bridge`, and `datamodel` moved into the project `common`) [#4564](https://github.com/raster-foundry/raster-foundry/pull/4564)
- Only analyses owned by the current user are displayed in the analysis browsing UI [#4357](https://github.com/raster-foundry/raster-foundry/pull/4357)
- Updated permission check logic for lab templates to make ownership filter work as expected [#4462](https://github.com/raster-foundry/raster-foundry/pull/4462)
- Unify S3 client interface usage [#4441](https://github.com/raster-foundry/raster-foundry/pull/4441)
- Moved common authentication logic to http4s-util subproject [#4496](https://github.com/raster-foundry/raster-foundry/pull/4496)
- Upgraded scala, javascript, and python rollbar clients [#4502](https://github.com/raster-foundry/raster-foundry/pull/4502)
- Added ability to download images as part of development environment setup [#4509](https://github.com/raster-foundry/raster-foundry/pull/4509)
- Allow users with edit permissions to edit the permissions of objects [#4490](https://github.com/raster-foundry/raster-foundry/pull/4490)

### Removed
- Removed unused dependency `geotrellis-raster-testkit`[#4482](https://github.com/raster-foundry/raster-foundry/pull/4482)
- Removed legacy tile server subproject and configuration [#4478](https://github.com/raster-foundry/raster-foundry/pull/4478)
- Removed unused metrics collection resources and application code [#4475](https://github.com/raster-foundry/raster-foundry/pull/4475), [#4493](https://github.com/raster-foundry/raster-foundry/pull/4493)
- Removed deprecated Gatling load tests [#4504](https://github.com/raster-foundry/raster-foundry/pull/4504)

### Fixed
- Shapes drawn within the scene search filter context can now be saved [#4474](https://github.com/raster-foundry/raster-foundry/pull/4474)
- Mosaics are again constructed with rasters instead of with IO[rasters][\#4498](<https://github.com/raster-foundry/raster-foundry/pull/4498>)
- Improved healthcheck logic in backsplash healthcheck endpoint [#4548](https://github.com/raster-foundry/raster-foundry/pull/4548)
- Fixed bug for publishing project page [#4578](https://github.com/raster-foundry/raster-foundry/pull/4578)

### Security
- Upgrade webpack-dev-server to address vulnerability (<https://nvd.nist.gov/vuln/detail/CVE-2018-14732>) [#4476](https://github.com/raster-foundry/raster-foundry/pull/4476)

## [1.16.4] - 2019-01-21
### Security
- Sanitized more user fields in search endpoint [#4505](https://github.com/raster-foundry/raster-foundry/pull/4505) and in platform member list and organization search [#4506](https://github.com/raster-foundry/raster-foundry/pull/4506)

## [1.16.3] - 2019-01-17
### Fixed
- Made backsplash respect the RF_LOG_LEVEL environment variable [#4483](https://github.com/raster-foundry/raster-foundry/pull/4483)

## [1.16.2] - 2019-01-14
### Added
- Added caching for histograms, rastersources, and tiles [#4429](https://github.com/raster-foundry/raster-foundry/pull/4429)[#4437](https://github.com/raster-foundry/raster-foundry/pull/4437)
- Made Raster Source configurable via environment variables to more easily test GDAL and GeoTiff Raster Sources [#4440](https://github.com/raster-foundry/raster-foundry/pull/4440)
- Parallelized mosaic construction from backsplash images [#4463](https://github.com/raster-foundry/raster-foundry/pull/4463)

### Fixed
- Removed faulty no-data interpretation in single band visualization [#4433](https://github.com/raster-foundry/raster-foundry/pull/4433)
- Fixed histogram calcuation + sampling logic and analysis rendering [#4438](https://github.com/raster-foundry/raster-foundry/pull/4438)

## 1.16.1 - 2019-01-03
### Fixed
- Added back the scenes mosaic endpoint [#4439](https://github.com/raster-foundry/raster-foundry/pull/4439)
- Fixed quick export of projects and analyses [#4459](https://github.com/raster-foundry/raster-foundry/pull/4459)
- Fixed route matching for map token authorization for analyses [#4463](https://github.com/raster-foundry/raster-foundry/pull/4463)
- Fixed permission checks for project and project datasource in lab analyses [#4466](https://github.com/raster-foundry/raster-foundry/pull/4466)

## [1.16.0] - 2019-01-02
### Added
- Templates can now be shared and filtered by ownership [#4357](https://github.com/raster-foundry/raster-foundry/pull/4357)
- Added a support email field to platform email settings [#4353](https://github.com/raster-foundry/raster-foundry/pull/4353)
- Supported team creation on user's team list page [#4345](https://github.com/raster-foundry/raster-foundry/pull/4345)
- Use java's gdal bindings for tile IO in backsplash and better separate concerns between fetching imagery and talking to the database / users [#4339](https://github.com/raster-foundry/raster-foundry/pull/4339)
- Added dropwizard metrics instrumentation to backsplash methods and endpoints [#4381](https://github.com/raster-foundry/raster-foundry/pull/4381)
- Added script for ad hoc tile server load testing [#4395](https://github.com/raster-foundry/raster-foundry/pull/4395), [#4404](https://github.com/raster-foundry/raster-foundry/pull/4404)
- Added graphite reporter to dropwizard metrics [#4398](https://github.com/raster-foundry/raster-foundry/pull/4398)
- Added alternative development runner/setup for testing API server and backsplash [#4402](https://github.com/raster-foundry/raster-foundry/pull/4402)
- Added configuration and helper script for gatling integration test results [#4410](https://github.com/raster-foundry/raster-foundry/pull/4410)
- Added gatling tests script that can be run in CI [#4424](https://github.com/raster-foundry/raster-foundry/pull/4424)
- Created AuthedAutoSlash middleware to make authentication and route matching cooperate [#4425](https://github.com/raster-foundry/raster-foundry/pull/4425)

### Changed
- Only analyses owned by the current user are displayed in the analysis browsing UI [#4357](https://github.com/raster-foundry/raster-foundry/pull/4357)
- Reorganized scala dependencies for package cleanliness and smaller bundles [#4301](https://github.com/raster-foundry/raster-foundry/pull/4301)
- If users are not requesting their own info, the returned other users' personal info are protected [#4360](https://github.com/raster-foundry/raster-foundry/pull/4360)
- Changed the data model of the return of `users/me/roles` endpoint [#4375](https://github.com/raster-foundry/raster-foundry/pull/4375)
- Added more aggressive timeout to backsplash for improved thread recovery [#4383](https://github.com/raster-foundry/raster-foundry/pull/4383)
- Decreased max classfile name length from 100 to 70 for CI reasons [#4388](https://github.com/raster-foundry/raster-foundry/pull/4388)
- Used a fixed number of threadPool for Backsplash [#4389](https://github.com/raster-foundry/raster-foundry/pull/4389)
- Made timeout length and number of threadPool configurable [#4389](https://github.com/raster-foundry/raster-foundry/pull/4389)
- Changed to use Fiber for threading in Backsplash services [#4396](https://github.com/raster-foundry/raster-foundry/pull/4396)
- Ignored errors from integration tests so that reports will always be written to s3 [#4406](https://github.com/raster-foundry/raster-foundry/pull/4406)
- Changed how the database transactor is passed to backsplash and API servers to prevent accidentally passing implicit execution contexts where they are not wanted [#4415](https://github.com/raster-foundry/raster-foundry/pull/4415)
- Added ability to test against several projects in gatling integration tests [#4416](https://github.com/raster-foundry/raster-foundry/pull/4416)
- Changed how to configure threadpools for backsplash and hikari [#4420](https://github.com/raster-foundry/raster-foundry/pull/4420)

### Fixed
- Made the status code for aoi creation on projects a 201 instead of a 200 [#4331](https://github.com/raster-foundry/raster-foundry/pull/4331)
- Opened access for group members to remove their group memberships [#4358](https://github.com/raster-foundry/raster-foundry/pull/4358)
- Used the correct field as outgoing email source in platform email settings [#4353](https://github.com/raster-foundry/raster-foundry/pull/4353)
- Fix deprecated use of route change listeners which caused window title to break [#4365](https://github.com/raster-foundry/raster-foundry/pull/4365)
- Fix project ownership filter persistance across pages [#4376](https://github.com/raster-foundry/raster-foundry/pull/4376)
- Fix logo on project share page and add error handling [/#4377](https://github.com/raster-foundry/raster-foundry/pull/4377)
- Address a number of unhandled promise chains on the frontend [#4380](https://github.com/raster-foundry/raster-foundry/pull/4380)
- Restored routes missing from backsplash after reintegration into RF main [#4382](https://github.com/raster-foundry/raster-foundry/pull/4382)
- Restored color correction [#4387](https://github.com/raster-foundry/raster-foundry/pull/4387)
- Fix logo on project share page and add error handling [/#4377](https://github.com/raster-foundry/raster-foundry/pull/4377)
- Address a number of unhandled promise chains on the frontend [#4380](https://github.com/raster-foundry/raster-foundry/pull/4380)
- Restored auth and error-handling [#4390](https://github.com/raster-foundry/raster-foundry/pull/4390)
- Aligned backsplash dockerfile with existing services [#4394](https://github.com/raster-foundry/raster-foundry/pull/4394)
- Upgraded geotrellis-server to handle thread safety issue that was causing SEGFAULTs in backsplash and for ArrayTile vs. Tile issues [#4399](https://github.com/raster-foundry/raster-foundry/pull/4399), [#4412](https://github.com/raster-foundry/raster-foundry/pull/4412), [#4426](https://github.com/raster-foundry/raster-foundry/pull/4426)
- Switched back to geotrellis for IO to shrink the space of failure conditions [#4414](https://github.com/raster-foundry/raster-foundry/pull/4414)
- Fetch histograms for multiband mosaics from the database again [#4417](https://github.com/raster-foundry/raster-foundry/pull/4417)
- Made single band tiles render without weird stripes [#4423](https://github.com/raster-foundry/raster-foundry/pull/4423)
- Fetched histograms from the database for single band visualization, falling back to from tiles [#4426](https://github.com/raster-foundry/raster-foundry/pull/4426)

## [1.15.0] - 2018-11-30
### Added
- Add Ansible role to install Nexus Repo Manager [#4277](https://github.com/raster-foundry/raster-foundry/pull/4277)
- Added S3 path suggestions in scene import modal when users upload imageries from S3 buckets [#4290](https://github.com/raster-foundry/raster-foundry/pull/4290)
- Enabled deleting lab templates on the frontend [#4287](https://github.com/raster-foundry/raster-foundry/pull/4287)
- Added support for viewing public projects using backsplash [#4299](https://github.com/raster-foundry/raster-foundry/pull/4299)
- Added script for reprocessing sentinel 2 scenes which were imported with the wrong number of bands [\4349]\(<https://github.com/raster-foundry/raster-foundry/pull/4349>

### Changed
- Populate user profiles from their identity tokens more intelligently [#4298](https://github.com/raster-foundry/raster-foundry/pull/4298)
- Improved project selection modal within the lab [#4324](https://github.com/raster-foundry/raster-foundry/pull/4324)
- Upgraded to Webpack 4 and replace broken libraries / componenets [#4199](https://github.com/raster-foundry/raster-foundry/pull/4199)
- Greatly reduce bundle size by using dynamic imports and using a more effective build process [#4199](https://github.com/raster-foundry/raster-foundry/pull/4199)

### Removed
- Removed email form validation from platform email settings page [#4294](https://github.com/raster-foundry/raster-foundry/pull/4294)

### Fixed
- Ensured tiles of non-standard sizes get resampled to the appropriate size before reaching users [#4281](https://github.com/raster-foundry/raster-foundry/pull/4281)
- Specifically handled bad paths to COGs when users create scenes [#4295](https://github.com/raster-foundry/raster-foundry/pull/4295)
- Fixed shapefile annotations export [#4300](https://github.com/raster-foundry/raster-foundry/pull/4300)
- Made s3 client tolerate buckets outside of its configured region [#4319](https://github.com/raster-foundry/raster-foundry/pull/4319)
- Fixed logging dependency stack to eliminate painfully verbose logging in backsplash [#4326](https://github.com/raster-foundry/raster-foundry/pull/4326)
- Fix thumbnail loading placeholder size [#4355](https://github.com/raster-foundry/raster-foundry/pull/4355)
- Fix hidden text field for scene image sources [#4355](https://github.com/raster-foundry/raster-foundry/pull/4355)
- Fix long source names for scenes [#4355](https://github.com/raster-foundry/raster-foundry/pull/4355)
- Duplicate ingest emails for users with inactive platform UGRs [#4359](https://github.com/raster-foundry/raster-foundry/pull/4359)

## [1.14.2] - 2018-11-19
### Fixed
- Fixed regressions causing non-termination of export and Landsat 8 import jobs [#4312](https://github.com/raster-foundry/raster-foundry/pull/4312), [#4313](https://github.com/raster-foundry/raster-foundry/pull/4313)

## [1.14.1] - 2018-11-13
### Fixed
- Upgraded maml to 0.0.15 and circe to 0.10.0 and make async jobs use cats-effect IOApp [#4288](https://github.com/raster-foundry/raster-foundry/pull/4288)
- Repaired short-lived infinite recursion in export async job [#4297](https://github.com/raster-foundry/raster-foundry/pull/4297)

## [1.14.0] - 2018-11-08
### Added
- Administration
  - Allow platforms to set a "From" email field in order to change notification "From" name [#\\4214](https://github.com/raster-foundry/raster-foundry/pull/4214)
  - Allow platform administrators to create uploads for other users within their platforms [#4237](https://github.com/raster-foundry/raster-foundry/pull/4237)
- Added summary endpoint for annotation groups to list the number of labels with different qualities (YES, NO, MISS, UNSURE) to support annotation applications [#4221](https://github.com/raster-foundry/raster-foundry/pull/4221)
- Added project histogram support for COG and Avro scenes in backsplash [#4190](https://github.com/raster-foundry/raster-foundry/pull/4190)
- Added map token and authorization header authentication to backsplash [#4271](https://github.com/raster-foundry/raster-foundry/pull/4271)
- Added project quick png export support to backsplash [#4273](https://github.com/raster-foundry/raster-foundry/pull/4273)
- Added service-level and total error-handling to backsplash tile server [#4258](https://github.com/raster-foundry/raster-foundry/pull/4258)

### Changed
- Small text edit to the Imports page [#4198](https://github.com/raster-foundry/raster-foundry/pull/4198)
- Updated package and assembly jar names [#3924](https://github.com/raster-foundry/raster-foundry/pull/3924), [#4222](https://github.com/raster-foundry/raster-foundry/pull/4222), [#4240](https://github.com/raster-foundry/raster-foundry/pull/4240)
- Change homepage "Create a new Template" button to "Create a new Analysis" [/#4224](https://github.com/raster-foundry/raster-foundry/pull/4224)
- Projects with > 30 scenes will not show a preview on the project list page [/#4231](https://github.com/raster-foundry/raster-foundry/pull/4231)
- Upgraded scala typelevel ecosystem [#4215](https://github.com/raster-foundry/raster-foundry/pull/4215)
- Images no longer require a non-empty list of bands when creating scenes [#4241](https://github.com/raster-foundry/raster-foundry/pull/4241)
- Switched to semi-automatic json codec derivation for query parameters [#4267](https://github.com/raster-foundry/raster-foundry/pull/4267)
- Added recalculation and update of project extent after scene deletion [#4283](https://github.com/raster-foundry/raster-foundry/pull/4283)

### Fixed
- Increase nginx buffer size & count for Scene, Tool, and Thumbnail requests [#4170](https://github.com/raster-foundry/raster-foundry/pull/4170)
- Add user button no longer shows for non-admins of teams and orgs [#4212](https://github.com/raster-foundry/raster-foundry/pull/4212)
- Fix undefined function call when selecting project scenes by clicking the map in advanced color correction view [#4212](https://github.com/raster-foundry/raster-foundry/pull/4212)
- Fix visualization of Planet scenes and fix bands used when generating COG scene thumbnails [#4238](https://github.com/raster-foundry/raster-foundry/pull/4238), [#4262](https://github.com/raster-foundry/raster-foundry/pull/4262)
- Stopped explicitly setting a nodata value in one step of ingest for Sentinel-2 and Landsat [#4324](https://github.com/raster-foundry/raster-foundry/pull/4234)
- Stopped combining Landsat 4 / 5 / 7 bands in random orders when converting them to COGs and added command to fix existing Landsat 4 / 5 / 7 scenes [#4242](https://github.com/raster-foundry/raster-foundry/pull/4242), [#4261](https://github.com/raster-foundry/raster-foundry/pull/4261)
- Cleaned up a project database test [#4248](https://github.com/raster-foundry/raster-foundry/pull/4248)
- Don't include name in intercom user init if it's the same as the email [#4247](https://github.com/raster-foundry/raster-foundry/pull/4247)
- Kick off ingests for scenes without scene types also [#4260](https://github.com/raster-foundry/raster-foundry/pull/4260)
- Separated connection and transaction execution contexts in database tests [#4264](https://github.com/raster-foundry/raster-foundry/pull/4264)
- More carefully managed system resources to prevent non-terminating asynchronous workflows [#4268](https://github.com/raster-foundry/raster-foundry/pull/4268)
- Made search feature more secure for endpoints that supply such query parameter [#4280](https://github.com/raster-foundry/raster-foundry/pull/4280)
- Fixed annotation click and edit bug when the annotation is uploaded from a zipped shapefile [#4282](https://github.com/raster-foundry/raster-foundry/pull/4282)

## [1.13.0] - 2018-10-10
### Added
- Disable blog feed and intercom initialization using webpack override file [#4162](https://github.com/raster-foundry/raster-foundry/pull/4162)
- Add support for google tag manager via webpack overrides [#4165](https://github.com/raster-foundry/raster-foundry/pull/4165)
- Added support for additional/future Planet asset types [#4184](https://github.com/raster-foundry/raster-foundry/pull/4184)

### Changed
- Switched to [keepachangelog](https://keepachangelog.com/en/1.0.0/) CHANGELOG format [#4159](https://github.com/raster-foundry/raster-foundry/pull/4159)
- Used production-hardened existing color correction for backsplash COGs instead of hand-rolled ad hoc color correction [#4160](https://github.com/raster-foundry/raster-foundry/pull/4160)
- Restricted sharing with everyone and platforms to superusers and platform admins [#4166](https://github.com/raster-foundry/raster-foundry/pull/4166)
- Added sbt configuration for auto-scalafmt [#4175](https://github.com/raster-foundry/raster-foundry/pull/4175)
- Displayed user information on template items when not created by the platform [#4172](https://github.com/raster-foundry/raster-foundry/pull/4172)
- Simplified authorization logic in backsplash [#4176](https://github.com/raster-foundry/raster-foundry/pull/4176)
- Added a global cache location for sharing artifacts across CI builds [#4181](https://github.com/raster-foundry/raster-foundry/pull/4181), [#4183](https://github.com/raster-foundry/raster-foundry/pull/4183), [#4186](https://github.com/raster-foundry/raster-foundry/pull/4186)
- Switched to using `sbt` to resolve dependencies [#4191](https://github.com/raster-foundry/raster-foundry/pull/4191)
- Users who are not admins of an organization can now correctly create teams and are automatically added to them [#4147](https://github.com/raster-foundry/raster-foundry/pull/4171)
- Added additional options for starting development server [#4192](https://github.com/raster-foundry/raster-foundry/pull/4192)

### Fixed
- Removed duplicate emails for repeated failures of the same upload [#4130](https://github.com/raster-foundry/raster-foundry/pull/4130)
- Used safer options for large tifs when processing uploads and ingests [#4131](https://github.com/raster-foundry/raster-foundry/pull/4131)
- Re-enabled datasource deletion and disable it if there is permission attached [#4140](https://github.com/raster-foundry/raster-foundry/pull/4140), [#4158](https://github.com/raster-foundry/raster-foundry/pull/4158)
- Fixed permission modal bug so that it won't hang after deleting permissions [#4174](https://github.com/raster-foundry/raster-foundry/pull/4174)
- Fixed issue with clamping imagery whose range was greater than, but included values between 0 and 255 [#4177](https://github.com/raster-foundry/raster-foundry/pull/4177)
- Included missing `pow` operation for decoding json representations of analyses [#4179](https://github.com/raster-foundry/raster-foundry/pull/4140), [#4155](https://github.com/raster-foundry/raster-foundry/issues/4155)

[Unreleased]: https://github.com/raster-foundry/raster-foundry/compare/v1.61.2...HEAD
[1.61.2]: https://github.com/raster-foundry/raster-foundry/compare/v1.61.1...v1.61.2
[1.61.1]: https://github.com/raster-foundry/raster-foundry/compare/v1.61.0...v1.61.1
[1.61.0]: https://github.com/raster-foundry/raster-foundry/compare/v1.60.1...v1.61.0
[1.60.1]: https://github.com/raster-foundry/raster-foundry/compare/v1.60.0...v1.60.1
[1.60.0]: https://github.com/raster-foundry/raster-foundry/compare/v1.59.0...v1.60.0
[1.59.0]: https://github.com/raster-foundry/raster-foundry/compare/v1.58.0...v1.59.0
[1.58.0]: https://github.com/raster-foundry/raster-foundry/compare/v1.57.0...v1.58.0
[1.57.0]: https://github.com/raster-foundry/raster-foundry/compare/v1.56.0...v1.57.0
[1.56.0]: https://github.com/raster-foundry/raster-foundry/compare/v1.55.0...v1.56.0
[1.55.0]: https://github.com/raster-foundry/raster-foundry/compare/v1.54.0...v1.55.0
[1.54.0]: https://github.com/raster-foundry/raster-foundry/compare/v1.53.0...v1.54.0
[1.53.0]: https://github.com/raster-foundry/raster-foundry/compare/v1.52.2...v1.53.0
[1.52.2]: https://github.com/raster-foundry/raster-foundry/compare/1.52.1...1.52.2
[1.52.1]: https://github.com/raster-foundry/raster-foundry/compare/1.52.0...1.52.1
[1.52.0]: https://github.com/raster-foundry/raster-foundry/compare/1.51.0...1.52.0
[1.51.0]: https://github.com/raster-foundry/raster-foundry/compare/1.50.0...1.51.0
[1.50.0]: https://github.com/raster-foundry/raster-foundry/compare/1.49.0...1.50.0
[1.49.0]: https://github.com/raster-foundry/raster-foundry/compare/1.48.0...1.49.0
[1.48.0]: https://github.com/raster-foundry/raster-foundry/compare/1.47.0...1.48.0
[1.47.0]: https://github.com/raster-foundry/raster-foundry/compare/1.46.1...1.47.0
[1.46.1]: https://github.com/raster-foundry/raster-foundry/compare/1.46.0...1.46.1
[1.46.0]: https://github.com/raster-foundry/raster-foundry/compare/1.45.0...1.46.0
[1.44.2]: https://github.com/raster-foundry/raster-foundry/compare/1.44.1...1.44.2
[1.44.1]: https://github.com/raster-foundry/raster-foundry/compare/1.44.0...1.44.1
[1.44.0]: https://github.com/raster-foundry/raster-foundry/compare/1.43.0...1.44.0
[1.43.0]: https://github.com/raster-foundry/raster-foundry/compare/1.42.0...1.43.0
[1.42.0]: https://github.com/raster-foundry/raster-foundry/compare/1.41.0...1.42.0
[1.41.0]: https://github.com/raster-foundry/raster-foundry/compare/1.40.3...1.41.0
[1.40.3]: https://github.com/raster-foundry/raster-foundry/compare/1.40.2...1.40.3
[1.40.2]: https://github.com/raster-foundry/raster-foundry/compare/1.40.1...1.40.2
[1.40.1]: https://github.com/raster-foundry/raster-foundry/compare/1.40.0...1.40.1
[1.40.0]: https://github.com/raster-foundry/raster-foundry/compare/1.39.0...1.40.0
[1.39.0]: https://github.com/raster-foundry/raster-foundry/compare/1.38.1...1.39.0
[1.38.1]: https://github.com/raster-foundry/raster-foundry/compare/1.38.0...1.38.1
[1.38.0]: https://github.com/raster-foundry/raster-foundry/compare/1.37.0...1.38.0
[1.37.0]: https://github.com/raster-foundry/raster-foundry/compare/1.36.0...1.37.0
[1.36.0]: https://github.com/raster-foundry/raster-foundry/compare/1.35.0...1.36.0
[1.35.0]: https://github.com/raster-foundry/raster-foundry/compare/1.34.1...1.35.0
[1.34.1]: https://github.com/raster-foundry/raster-foundry/compare/1.34.0...1.34.1
[1.34.0]: https://github.com/raster-foundry/raster-foundry/compare/1.33.0...1.34.0
[1.33.0]: https://github.com/raster-foundry/raster-foundry/compare/1.32.1...1.33.0
[1.32.1]: https://github.com/raster-foundry/raster-foundry/compare/1.32.0...1.32.1
[1.32.0]: https://github.com/raster-foundry/raster-foundry/compare/1.31.0...1.32.0
[1.31.0]: https://github.com/raster-foundry/raster-foundry/compare/1.30.0...1.31.0
[1.30.0]: https://github.com/raster-foundry/raster-foundry/compare/1.29.1...1.30.0
[1.29.1]: https://github.com/raster-foundry/raster-foundry/compare/1.29.0...1.29.1
[1.29.0]: https://github.com/raster-foundry/raster-foundry/compare/1.28.0...1.29.0
[1.28.0]: https://github.com/raster-foundry/raster-foundry/compare/1.27.0...1.28.0
[1.27.0]: https://github.com/raster-foundry/raster-foundry/compare/1.26.0...1.27.0
[1.26.0]: https://github.com/raster-foundry/raster-foundry/compare/1.25.1...1.26.0
[1.25.1]: https://github.com/raster-foundry/raster-foundry/tree/1.25.1
[1.25.0]: https://github.com/raster-foundry/raster-foundry/tree/1.25.0
[1.24.0]: https://github.com/raster-foundry/raster-foundry/tree/1.24.0
[1.23.0]: https://github.com/raster-foundry/raster-foundry/tree/1.23.0
[1.22.0]: https://github.com/raster-foundry/raster-foundry/tree/1.22.0
[1.21.3]: https://github.com/raster-foundry/raster-foundry/tree/1.21.3
[1.21.2]: https://github.com/raster-foundry/raster-foundry/tree/1.21.2
[1.21.1]: https://github.com/raster-foundry/raster-foundry/tree/1.21.1
[1.21.0]: https://github.com/raster-foundry/raster-foundry/tree/1.21.0
[1.20.0]: https://github.com/raster-foundry/raster-foundry/tree/1.20.0
[1.19.0]: https://github.com/raster-foundry/raster-foundry/tree/1.19.0
[1.18.1]: https://github.com/raster-foundry/raster-foundry/tree/1.18.1
[1.18.0]: https://github.com/raster-foundry/raster-foundry/tree/1.18.0
[1.17.1]: https://github.com/raster-foundry/raster-foundry/tree/1.17.1
[1.17.0]: https://github.com/raster-foundry/raster-foundry/tree/1.17.0
[1.16.4]: https://github.com/raster-foundry/raster-foundry/tree/1.16.4
[1.16.3]: https://github.com/raster-foundry/raster-foundry/tree/1.16.3
[1.16.2]: https://github.com/raster-foundry/raster-foundry/tree/1.16.2
[1.16.0]: https://github.com/raster-foundry/raster-foundry/tree/1.16.0
[1.15.0]: https://github.com/raster-foundry/raster-foundry/tree/1.15.0
[1.14.2]: https://github.com/raster-foundry/raster-foundry/tree/1.14.2
[1.14.1]: https://github.com/raster-foundry/raster-foundry/tree/1.14.1
[1.14.0]: https://github.com/raster-foundry/raster-foundry/tree/1.14.0
[1.13.0]: https://github.com/raster-foundry/raster-foundry/tree/1.13.0

# Change Log

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

## [1.3.0](https://github.com/raster-foundry/raster-foundry/tree/1.3.0) (2018-06-29)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/1.2.1...1.3.0)

## [1.2.1](https://github.com/raster-foundry/raster-foundry/tree/1.2.1) (2018-06-26)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/1.2.0...1.2.1)

## [1.2.0](https://github.com/raster-foundry/raster-foundry/tree/1.2.0) (2018-06-25)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/1.1.1...1.2.0)

## [1.1.1](https://github.com/raster-foundry/raster-foundry/tree/1.1.1) (2018-06-19)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/1.1.0...1.1.1)

## [1.1.0](https://github.com/raster-foundry/raster-foundry/tree/1.1.0) (2018-06-18)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/1.0.3...1.1.0)

## [1.0.3](https://github.com/raster-foundry/raster-foundry/tree/1.0.3) (2018-06-14)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/1.0.2...1.0.3)

## [1.0.2](https://github.com/raster-foundry/raster-foundry/tree/1.0.2) (2018-06-12)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/1.0.1...1.0.2)

## [1.0.1](https://github.com/raster-foundry/raster-foundry/tree/1.0.1) (2018-06-08)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/1.0.0...1.0.1)

## [1.0.0](https://github.com/raster-foundry/raster-foundry/tree/1.0.0) (2018-06-08)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.36.0...1.0.0)

## [0.36.0](https://github.com/raster-foundry/raster-foundry/tree/0.36.0) (2018-05-23)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.35.0...0.36.0)

## [0.35.0](https://github.com/raster-foundry/raster-foundry/tree/0.35.0) (2018-05-16)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.34.2...0.35.0)

## [0.34.2](https://github.com/raster-foundry/raster-foundry/tree/0.34.2) (2018-05-09)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.34.1...0.34.2)

## [0.34.1](https://github.com/raster-foundry/raster-foundry/tree/0.34.1) (2018-05-01)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.34.0...0.34.1)

## [0.34.0](https://github.com/raster-foundry/raster-foundry/tree/0.34.0) (2018-05-01)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.33.3...0.34.0)

## [0.33.3](https://github.com/raster-foundry/raster-foundry/tree/0.33.3) (2018-03-19)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.33.2...0.33.3)

## [0.33.2](https://github.com/raster-foundry/raster-foundry/tree/0.33.2) (2018-03-16)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.33.1...0.33.2)

## [0.33.1](https://github.com/raster-foundry/raster-foundry/tree/0.33.1) (2018-03-13)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.33.0...0.33.1)

## [0.33.0](https://github.com/raster-foundry/raster-foundry/tree/0.33.0) (2018-03-13)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.32.0...0.33.0)

## [0.32.0](https://github.com/raster-foundry/raster-foundry/tree/0.32.0) (2018-03-06)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.31.0...0.32.0)

## [0.31.0](https://github.com/raster-foundry/raster-foundry/tree/0.31.0) (2018-01-19)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.30.0...0.31.0)

## [0.30.0](https://github.com/raster-foundry/raster-foundry/tree/0.30.0) (2018-01-18)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.29.0...0.30.0)

## [0.29.0](https://github.com/raster-foundry/raster-foundry/tree/0.29.0) (2018-01-04)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.28.0...0.29.0)

## [0.28.0](https://github.com/raster-foundry/raster-foundry/tree/0.28.0) (2017-12-11)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.27.0...0.28.0)

## [0.27.0](https://github.com/raster-foundry/raster-foundry/tree/0.27.0) (2017-12-07)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.26.0...0.27.0)

## [0.26.0](https://github.com/raster-foundry/raster-foundry/tree/0.26.0) (2017-12-01)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.25.0...0.26.0)

## [0.25.0](https://github.com/raster-foundry/raster-foundry/tree/0.25.0) (2017-10-08)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.24.0...0.25.0)

## [0.24.0](https://github.com/raster-foundry/raster-foundry/tree/0.24.0) (2017-10-05)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.23.0...0.24.0)

## [0.23.0](https://github.com/raster-foundry/raster-foundry/tree/0.23.0) (2017-10-05)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.22.0...0.23.0)

## [0.22.0](https://github.com/raster-foundry/raster-foundry/tree/0.22.0) (2017-09-27)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.21.0...0.22.0)

## [0.21.0](https://github.com/raster-foundry/raster-foundry/tree/0.21.0) (2017-09-25)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.20.2...0.21.0)

## [0.20.2](https://github.com/raster-foundry/raster-foundry/tree/0.20.2) (2017-08-24)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.20.1...0.20.2)

## [0.20.1](https://github.com/raster-foundry/raster-foundry/tree/0.20.1) (2017-08-24)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.20.0...0.20.1)

## [0.20.0](https://github.com/raster-foundry/raster-foundry/tree/0.20.0) (2017-08-22)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.20.0-test-do-not-merge...0.20.0)

## [0.20.0-test-do-not-merge](https://github.com/raster-foundry/raster-foundry/tree/0.20.0-test-do-not-merge) (2017-08-22)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.19.0...0.20.0-test-do-not-merge)

## [0.19.0](https://github.com/raster-foundry/raster-foundry/tree/0.19.0) (2017-08-13)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.18.1...0.19.0)

## [0.18.1](https://github.com/raster-foundry/raster-foundry/tree/0.18.1) (2017-08-10)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.18.0...0.18.1)

## [0.18.0](https://github.com/raster-foundry/raster-foundry/tree/0.18.0) (2017-08-08)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.17.0...0.18.0)

## [0.17.0](https://github.com/raster-foundry/raster-foundry/tree/0.17.0) (2017-07-05)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.16.0...0.17.0)

## [0.16.0](https://github.com/raster-foundry/raster-foundry/tree/0.16.0) (2017-06-27)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.15.0...0.16.0)

## [0.15.0](https://github.com/raster-foundry/raster-foundry/tree/0.15.0) (2017-06-26)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.14.0...0.15.0)

## [0.14.0](https://github.com/raster-foundry/raster-foundry/tree/0.14.0) (2017-06-21)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.13.0...0.14.0)

## [0.13.0](https://github.com/raster-foundry/raster-foundry/tree/0.13.0) (2017-06-19)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.12.0...0.13.0)

## [0.12.0](https://github.com/raster-foundry/raster-foundry/tree/0.12.0) (2017-06-16)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.11.0...0.12.0)

## [0.11.0](https://github.com/raster-foundry/raster-foundry/tree/0.11.0) (2017-06-01)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.10.1...0.11.0)

## [0.10.1](https://github.com/raster-foundry/raster-foundry/tree/0.10.1) (2017-05-27)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.10.0...0.10.1)

## [0.10.0](https://github.com/raster-foundry/raster-foundry/tree/0.10.0) (2017-05-26)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.9.1...0.10.0)

## [0.9.1](https://github.com/raster-foundry/raster-foundry/tree/0.9.1) (2017-05-26)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.9.0...0.9.1)

## [0.9.0](https://github.com/raster-foundry/raster-foundry/tree/0.9.0) (2017-05-25)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.8.0...0.9.0)

## [0.8.0](https://github.com/raster-foundry/raster-foundry/tree/0.8.0) (2017-05-25)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.7.0...0.8.0)

## [0.7.0](https://github.com/raster-foundry/raster-foundry/tree/0.7.0) (2017-05-19)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.6.0...0.7.0)

## [0.6.0](https://github.com/raster-foundry/raster-foundry/tree/0.6.0) (2017-05-15)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.5.0...0.6.0)

## [0.5.0](https://github.com/raster-foundry/raster-foundry/tree/0.5.0) (2017-05-05)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.4.0...0.5.0)

## [0.4.0](https://github.com/raster-foundry/raster-foundry/tree/0.4.0) (2017-05-03)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.3.0...0.4.0)

## [0.3.0](https://github.com/raster-foundry/raster-foundry/tree/0.3.0) (2017-04-28)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.2.0...0.3.0)

## [0.2.0](https://github.com/raster-foundry/raster-foundry/tree/0.2.0) (2017-03-09)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.1.0...0.2.0)

## [0.1.0](https://github.com/raster-foundry/raster-foundry/tree/0.1.0) (2017-02-16)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/0.0.1...0.1.0)

## [0.0.1](https://github.com/raster-foundry/raster-foundry/tree/0.0.1) (2016-07-14)

[Full Changelog](https://github.com/raster-foundry/raster-foundry/compare/31e56cea5aaa742dbcf162900326afbcf9adb497...0.0.1)



* *This Change Log was automatically generated by [github_changelog_generator](https://github.com/skywinder/Github-Changelog-Generator)*
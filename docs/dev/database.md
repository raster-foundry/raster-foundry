# The Dev Database
The dev database was created to assist with creating data for testing, without unduly slowing down development machines

## Where to get it
During initial setup, the dev database is pulled from s3.
To refresh your database, you can run `./scripts/load_development_data` inside the vagrant VM

## What's in the database
* A default user: `dev@rasterfoundry.com`, with a password of `rasterfoundry`
* A default root user: `root@rasterfoundry.com`, with a passsword of `rasterfoundry`
* 3 organizations: `root`, `Public` (the default for users), and `Private Organization` (for later testing of organization permissions / visibility)
* Some user uploads
* A variety of ingested scenes under the dev user, in projects:
  * Overlapping scenes for use with change detection
  * A set of scenes which need color correction
  * User imported high resolution flight imagery
* Default projects for new users, created under the `default_projects` psuedo-user then automatically copied when a new user logs in

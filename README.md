# Raster Foundry

## Getting Started

A virtual machine is used to encapsulate Docker dependencies. `docker-compose` is used within the VM to manage running the application and developing against it.

### Requirements

- Vagrant 1.8+
- VirtualBox 5.0+
- Ansible 1.8+ (on host)
- AWS CLI 1.10+
- AWS Account (to store artifacts, secrets)
- [Auth0 Account](https://auth0.com/) (user management)
- [Rollbar Account](https://rollbar.com/) (error reporting -- optional)

#### Setting Up AWS Account

There are a set of tasks necessary before starting development in order to provision Raster Foundry. Raster Foundry depends heavily on AWS resources and using AWS resources to manage secrets/containers/artifacts in development. If only local development is being done, the primary resource that will be used are S3 buckets to store secrets.

In the AWS account you need to create a few buckets for the following:
 - A bucket to house raw data (e.g. geotiffs, JPEG2000, ingest definitions, etc.)
 - A config bucket that will store secrets for development and or other environments, an exported database for development data
 - A bucket to house processed data (e.g. thumbnails, processed raster RDDs)

The names of the buckets are not important, but they should be memorable and easy to parse for your own sake. On your host machine you need to set up an AWS profile for the account with the S3 buckets. For instance, to set up an AWS profile called `raster-foundry` with the AWS cli the following command would be used:
```
$ aws configure --profile raster-foundry
```

You will be prompted for an access key and secret key.

#### Setting Development Environment Variables

The `.env.template` file is a template file with environment variables that get injected into running containers during development. This file should be copied into the AWS config bucket created after filling in sensitive information (replacing all `PLACEHOLDER` values with appropriate values for your AWS setup). When provisioning this file is copied to the development environment and injected into containers with `docker-compose`.

In addition to setting up an AWS account, you must register for an Auth0 account to produce secrets to use in the `.env` file. You need to go through setting up an application and copying over the client IDs, domain, and secret.

Additionally, if you want to exercise token management in the application, you need to generate a management API app to handle managing the generation of [refresh tokens for users via the management API](https://auth0.com/docs/api/management/v2/tokens). This is not necessary for most functionality in the application and can be deferred until later if you desire.

The last thing to set up with Auth0 are the allowed callback URLs and logout URLs. These need to be edited to allow interaction for local development from `localhost:9091` and `localhost:9100`.

### Development

Vagrant is used to manage VirtualBox provisioning and configuration. Raster Foundry follows the approach outlined [here](https://githubengineering.com/scripts-to-rule-them-all/) ("Scripts to Rule Them All") to have as consistent a development experience as possible. Almost all interaction with consoles and servers can be managed via calls to a script located in `./scripts`. Default values for the S3 config and data buckets in addition to AWS profile will be used if they are not set with an environment variable. Before running vagrant, these should be injected into your shell environment:
```bash
export RF_AWS_PROFILE=raster-foundry
export RF_SETTINGS_BUCKET=rasterfoundry-development-config-us-east-1
export RF_ARTIFACTS_BUCKET=rasterfoundry-global-artifacts-us-east-1
```

After exporting your environment settings, you are ready to get started:

```bash
$ vagrant up
$ vagrant ssh
$ ./scripts/server
```

Use `vagrant up` to provision a virtual machine. During provisioning `docker` and `docker-compose` will be installed on the guest machine. Additionally, docker images will be downloaded for the database and created for the `akka-http` application server.

Once the machine is provisioned you can start services or development by ssh-ing into the machine (`vagrant ssh`) and using the helper scripts in the `/opt/raster-foundry/scripts` directory.

If you do not have a development database to seed your database with, you will need to initialize the database with `mg init` inside an `sbt` console `./scripts/console api-server ./sbt`

Development workflow varies by developer, but a typical development experience might include the following:

 - Create a new feature branch
 - Start up the vagrant machine with `vagrant up --provision`
 - Get an `sbt` console open using `./scripts/console api-server ./sbt`
 - Make changes to Scala code
 - Try compiling (`~compile`) or running the service to inspect it (`~api/run`)

### Migrations

Database migrations are managed using [scala-forklift](https://github.com/lastland/scala-forklift). The `scala-forklift` [example project](https://github.com/lastland/scala-forklift/tree/develop/example) provides a good overview and walkthrough of how the various components fit together, and how to manage migrations.

To initialize migrations on a database for the first time, run `mg init` within an `sbt console`. This creates a `__migrations__` table in the database to track which migrations have been applied. After the database has been initialized, all unapplied migrations may be applied by running `mg update` and then `mg apply`. Please note: the `mg migrate` command should be avoided because it invokes the code generation feature of forklift. This feature is not used in the `raster-foundry` project.

The workflow for creating a new migration is:

 - Open an `sbt` console using `./scripts/console api-server ./sbt`
 - Run `mg new s` for a `SQL` migration
   - The migration file is output to `migrations/src_migrations/main/scala/{VERSION_NUM}.scala`
 - Edit this file to perform the desired migration logic
 - Run `mg update` followed by `mg apply`
   - This executes the migration
   - Press `ENTER` once the migration command has completed
 - `mg update` will create a symlink with an absolute path to the migration. This path won't work in all environments, so you should run `./scripts/fix-migration migration_number` from the vm to update the symlink to a relative path.

#### Frontend Development

To do frontend development you will want to install [`nvm`](https://github.com/creationix/nvm#install-script) and use at least version 6.9+ (`lts/boron`). Once using `nvm`, install [yarn](https://yarnpkg.com/) with `npm install -g yarn`. After following the directions above for starting the VM, start the API server and other backend services by running `./scripts/server`.

Then _outside_ the VM, while the server is still running, run `yarn run start` while inside the `app-frontend/` directory. This will start a `webpack-dev-server` on port 9091 that will auto-reload after javascript and styling changes.

The two options to rebuild the static assets served by Nginx:

 - Run `yarn run build` outside the VM
 - Run `./scripts/console app-frontend "yarn run build"`
 - Run `./scripts/setup` (will also rebuild application server)

To run tests you can do one of the following (in order of speed):

 - Run `yarn run test` outside the VM (or `yarn run test-watch`)
 - Run `./scripts/test` inside the VM (will also run additional project tests)

#### Frontend Theming

Frontend theming should only be used if you intend on forking and white labeling the application. Theming of the frontend application can be done easily with a few tweaks to the `scss`. To get theming working correctly, follow these instructions: 

 - Edit `app-frontend/src/assets/styles/sass/app.scss` and uncomment the two blocks of code which reference theme files. This will turn the theme files _on_.
 - All theme overrides will then be written inside of `app-frontend/src/assets/styles/sass/theme`
     - app-wide variables for changing fonts, colors, etc. are located in `app-frontend/src/assets/styles/sass/theme/settings`.
     - app-wide variables for changing build options, basemaps, and app name are located in `app-frontend/config/webpack/overrides.js`. To start, copy the template file located in the same directory. Variables currently available for configuration are pre-populated at the top of the file.
     - `_core.scss` should contain the bulk of style overrides which `/settings/` does **not** cover.
     - **Tip:** You can mimic the main application `scss` structure inside of `/theme/` and `@import` the files into `_core.scss`
 - WIP: we are still working out the kinks for icon fonts and branding assets.

Due to active development to Raster Foundry, some aspects of theming might break and will need active maintenance. 

## Ports

The Vagrant configuration maps the following host ports to services running in the virtual machines. Ports can be overridden for individual developers using environment variables

| Service                   | Port                            | Environment Variable |
|---------------------------|---------------------------------|----------------------|
| Application Frontend      | [`9091`](http://localhost:9091) | `RF_PORT_9091`       |
| Nginx (api)               | [`9100`](http://localhost:9100) | `RF_PORT_9100`       |
| Nginx (tiler)             | [`9101`](http://localhost:9101) | `RF_PORT_9101`       |
| Application Server (akka) | [`9000`](http://localhost:9000) | `RF_PORT_9000`       |
| Tile Server (akka)        | [`9900`](http://localhost:9900) | `RF_PORT_9900`       |
| Airflow UI                | [`8080`](http://localhost:8080) | `RF_PORT_8080`       |
| Airflow Flower            | [`5555`](http://localhost:5555) | `RF_PORT_5555`       |

## Scripts

Helper and development scripts are located in the `./scripts` directory at the root of this project. These scripts are designed to encapsulate and perform commonly used actions such as starting a development server, accessing a development console, or running tests.

| Script Name             | Purpose                                                      |
|-------------------------|--------------------------------------------------------------|
| `bootstrap`             | Pulls/builds necessary containers                            |
| `setup`                 | Runs migrations, installs dependencies, etc.                 |
| `server`                | Starts a development server                                  |
| `console`               | Gives access to a running container via `docker-compose run` |
| `psql`                  | Drops you into a `psql` console.                             |
| `test`                  | Runs tests and linters for project                           |
| `cibuild`               | Invoked by CI server and makes use of `test`.                |
| `cipublish`             | Publish container images to container image repositories.    |
| `load_development_data` | Load data for development purposes                           |
| `publish-jars`          | Publish JAR artifacts to S3                                  |

## Testing

Run all the tests:

```bash
$ ./scripts/test
```

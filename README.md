# Raster Foundry

## Getting Started

A virtual machine is used to encapsulate Docker dependencies. `docker-compose` is used within the VM to manage running the application and developing against it.

### Requirements

- Vagrant 1.8+
- VirtualBox 5.0+
- Ansible 1.8+ (on host)
- AWS CLI 1.10+

On your host machine you need to set up a `raster-foundry` profile for the Raster Foundry AWS account using the following command:

```
$ aws configure --profile raster-foundry
```

You will be prompted for an access key and secret key.

### Development

_tldr_
```bash
$ vagrant up
$ vagrant ssh
$ ./scripts/server
```

Use `vagrant up` to provision a virtual machine. During provisioning `docker` and `docker-compose` will be installed on the guest machine. Additionally, docker images will be downloaded for the database and created for the `akka-http` application server.

Once the machine is provisioned you can start services or development by ssh-ing into the machine (`vagrant ssh`) and using the helper scripts in the `/opt/raster-foundry/scripts` directory.

Development workflow varies by developer, but a typical development experience might include the following:

 - Create a new feature branch
 - Start up the vagrant machine with `vagrant up --provision`
 - Get an `sbt` console open using `./scripts/console api-server ./sbt`
 - Make changes to Scala code
 - Try compiling (`~compile`) or running the service to inspect it (`~app/run`)

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

#### Frontend Development

To do frontend development you will want to install [`nvm`](https://github.com/creationix/nvm#install-script) and use at least version 6.9+ (`lts/boron`). Once using `nvm`, install [yarn](https://yarnpkg.com/) with `npm install -g yarn`. After following the directions above for starting the VM, start the API server and other backend services by running `./scripts/server`.

Then _outside_ the VM, while the server is still running, run `yarn run start` while inside the `app-frontend/` directory. This will start a `webpack-dev-server` on port 9091 that will auto-reload after javascript and styling changes.

There are three options to rebuild the static assets served by Nginx:

 - Run `yarn run build` outside the VM
 - Run `./scripts/console app-frontend "yarn run build"`
 - Run `./scripts/setup` (will also rebuild application server)

To run tests you can do one of the following (in order of speed):

 - Run `yarn run test` outside the VM (or `yarn run test-watch`)
 - Run `./scripts/console app-frontend "yarn run test"` inside the VM
 - Run `./scripts/test` inside the VM (will also run additional project tests)

## Ports

The Vagrant configuration maps the following host ports to services running in the virtual machines. Ports can be overridden for individual developers using environment variables

| Service                   | Port                            | Environment Variable |
|---------------------------|---------------------------------|----------------------|
| Application Frontend      | [`9091`](http://localhost:9091) | `RF_PORT_9091`       |
| Nginx                     | [`9100`](http://localhost:9100) | `RF_PORT_9100`       |
| Application Server (akka) | [`9000`](http://localhost:9000) | `RF_PORT_9000`       |
| Tile Server (akka)        | [`9900`](http://localhost:9900) | `RF_PORT_9900`       |
| Airflow UI                | [`8080`](http://localhost:8080) | `RF_PORT_8080`       |
| Airflow Flower            | [`5555`](http://localhost:5555) | `RF_PORT_5555`       |
| Swagger Editor            | [`9090`](http://localhost:9090) | `RF_PORT_9090`       |
| Swagger UI                | [`9999`](http://localhost:9999) | `RF_PORT_9999`       |


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

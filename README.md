# Raster Foundry

## Getting Started

A virtual machine is used to encapsulate docker dependencies. `docker-compose` is used within the VM to manage running the application and developing against it.

### Requirements
- Vagrant 1.8.0+
- VirtualBox 5.0.14+
- Ansible 1.8.0+ (on host)
- AWS CLI 1.10+

On your host machine you need to set up a `raster-foundry` profile for the Raster Foundry AWS account using the following command:
```
aws configure --profile raster-foundry
```
You will be prompted for an access key and secret key.

### Development

_tldr_
```bash
  vagrant up
  vagrant ssh
  cd /opt/raster-foundry/
  ./scripts/server
```

Use `vagrant up` to provision a virtual machine. During provisioning `docker` and `docker-compose` will be installed on the guest machine. Additionally, docker images will be downloaded for the database and created for the `akka-http` application server.

Once the machine is provisioned you can start services or development by ssh-ing into the machine (`vagrant ssh`) and using the helper scripts in the `/opt/raster-foundry/scripts` directory.

Development workflow varies by developer, but a typical development experience might include the following:
 - create a new feature branch
 - start up the vagrant machine with `vagrant up --provision`
 - get an `sbt` console open using `./scripts/console app-server ./sbt`
 - make changes to scala code
 - try compiling (`~compile`) or running the service to inspect it (`~app/run`)

### Migrations

Database migrations are managed using [scala-forklift](https://github.com/lastland/scala-forklift). The `scala-forklift` [example project](https://github.com/lastland/scala-forklift/tree/develop/example) provides a good overview and walkthrough of how the various components fit together, and how to manage migrations.

To initialize migrations on a database for the first time, run `mg init` within an `sbt console`. This creates a `__migrations__` table in the database to track which migrations have been applied. After the database has been initialized, all unapplied migrations may be applied by running `mg update` and then `mg apply`. Please note: the `mg migrate` command should be avoided because it invokes the code generation feature of forklift. This feature is not used in the `raster-foundry` project.

The workflow for creating a new migration is:
 - open an `sbt` console using `./scripts/console app-server ./sbt`
 - run `mg new s` for a `SQL` migration
   - the migration file is output to `migrations/src_migrations/main/scala/{VERSION_NUM}.scala`
 - edit this file to perform the desired migration logic
 - run `mg update` followed by `mg apply`
   - this executes the migration
   - press `ENTER` once the migration command has completed

#### Frontend Development

To do frontend development you will want to install [`nvm`](https://github.com/creationix/nvm#install-script) and use a recent version of node (e.g. 4+). After following the directions above for starting the VM, start the API server and other backend services by running `./scripts/server`.

Then _outside_ the VM, while the server is still running, run `npm start` while inside the `app-frontend/` directory. This will start a `webpack-dev-server` on port 9090 that will auto-reload after javascript and styling changes.

There are three options to rebuild the static assets served by nginx:
 - run `npm run build` outside the VM
 - run `./scripts/console app-frontend "npm run build"`
 - run `./scripts/setup` (will also rebuild application server)

To run tests you can do one of the following (in order of speed):
 - run `npm run test` outside the VM (or `npm run test-watch`)
 - run `./scripts/console app-frontend "npm run test"` inside the VM
 - run `./scripts/test` inside the VM (will also run additional project tests)

## Ports

The Vagrant configuration maps the following host ports to services running in the virtual machines. Ports can be overridden for individual developers using environment variables

| Service                   | Port                            | Environment Variable |
|---------------------------|---------------------------------|----------------------|
| Application Frontend      | [`9090`](http://localhost:9091) | `RF_PORT_9091`       |
| Nginx                     | [`9100`](http://localhost:9100) | `RF_PORT_9100`       |
| Application Server (akka) | [`9000`](http://localhost:9000) | `RF_PORT_9000`       |
| Database                  | `5432`                          | `RF_PORT_5432`       |
| Airflow UI                | [`8080`](http://localhost:8080) | `RF_PORT_8080`       |
| Airflow Flower            | [`5555`](http://localhost:5555) | `RF_PORT_5555`       |
| Swagger Editor            | [`9090`](http://localhost:9090) | `RF_PORT_9090`       |
| Swagger UI                | [`9999`](http://localhost:9999) | `RF_PORT_9999`       |


## Scripts

Helper and development scripts are located in the `./scripts` directory at the root of this project. These scripts are designed to encapsulate and perform commonly used actions such as starting a development server, accessing a development console, or running tests.

| Script Name             | Purpose                                                      |
|-------------------------|--------------------------------------------------------------|
| `bootstrap`             | Pulls/builds necessary containers                            |
| `setup`                 | Runs migrations, installs dependencies, etc                  |
| `server`                | Starts a development server                                  |
| `console`               | Gives access to a running container via `docker-compose run` |
| `test`                  | Runs tests and linters for project                           |
| `cibuild`               | Invoked by CI server and makes use of `test`.                |
| `cipublish`             | Publish container images to container image repositories.    |
| `load_development_data` | Load data for development purposes                           |

## Testing

Run all the tests:

```bash
  ./scripts/test
```

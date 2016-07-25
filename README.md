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
 - try compiling (`~compile`) or running the service to inspect it (`~run`)

## Ports

The Vagrant configuration maps the following host ports to services running in the virtual machines. Ports can be overridden for individual developers using environment variables

| Service                   | Port                            | Environment Variable |
|---------------------------|---------------------------------|----------------------|
| Application Server (akka) | [`9000`](http://localhost:9000) | `RF_PORT_9000`       |
| Database                  | `5432`                          | `RF_PORT_5432`       |
| Swagger Editor            | [`8080`](http://localhost:8080) | `RF_PORT_8080`       |


## Scripts

Helper and development scripts are located in the `./scripts` directory at the root of this project. These scripts are designed to encapsulate and perform commonly used actions such as starting a development server, accessing a development console, or running tests.

| Script Name | Purpose                                                      |
|-------------|--------------------------------------------------------------|
| `bootstrap` | Pulls/builds necessary containers                            |
| `setup`     | Runs migrations, installs dependencies, etc                  |
| `server`    | Starts a development server                                  |
| `console`   | Gives access to a running container via `docker-compose run` |
| `test`      | Runs tests and linters for project                           |

## Testing

Run all the tests:

```bash
  ./scripts/test
```

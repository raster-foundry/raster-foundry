# Raster Foundry

## Phase 1

The final state of Phase 1 may be found by checking out the `0.0.1` tag.

## Local Development

### Getting Started

Use the following command to bring up a local development environment:

```bash
$ vagrant up
```

The application will now be running at

After pulling in new commits, you may need to run the following commands:

```bash
# command here
```

Watch the JavaScript and SASS files for changes:

```bash
# command here
```

```bash
$ vagrant provision <VM name>
```

After provisioning is complete, you can login to the application server and execute Django management commands:

```bash
# command here
```

### Ports

The Vagrant configuration maps the following host ports to services running in the virtual machines.

Service                | Port | URL
---------------------- | -----| ------------------------------------------------
                       | xxxx | [http://localhost:xxxx](http://localhost:xxxx)

### Caching

In order to speed up things up, you may want to consider leveraging the `vagrant-cachier` plugin. If installed, it is automatically used by Vagrant.

### Test Mode

In order to run the app in test mode, which simulates the production static asset bundle, reprovision with `VAGRANT_ENV=TEST vagrant provision`.

### Testing

Run all the tests:

```bash
# command here
```

#### Adding JS dependencies

Add instructions here

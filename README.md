# Raster Foundry

[![Join the chat at https://gitter.im/azavea/raster-foundry](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/azavea/raster-foundry?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

## Local Development

A combination of Vagrant 1.6+ and Ansible 1.8+ is used to setup the development environment for this project. The project consists of the following virtual machines:

- `app`
- `services`

The `app` virtual machine contains an instance of the Django application, `services` contains:

- PostgreSQL
- Pgweb
- Redis
- Logstash
- Kibana
- Graphite
- Statsite

### Getting Started

Use the following command to bring up a local development environment:

```bash
$ vagrant up
```

The application will now be running at [http://localhost:8000](http://localhost:8000).

After pulling in new commits, you may need to run the following two commands:

```bash
$ ./scripts/manage.sh migrate
$ ./scripts/bundle.sh
```

See debug messages from the web app server:

```bash
$ ./scripts/debugserver.sh
```

Watch the JavaScript and SASS files for changes:

```bash
$ ./scripts/bundle.sh --debug --watch
```

When creating new JavaScript or SASS files, you may need to stop and restart the bundle script.

If you add a JS dependency and want it to be included in the `vendor.js` bundle, you will need to update the `JS_DEPS` array in `bundle.sh` accordingly.

If changes were made to the one of the VM's configuration or requirements since the last time you provisioned, you'll need to reprovision.

```bash
$ vagrant provision <VM name>
```

After provisioning is complete, you can login to the application server and execute Django management commands:

```bash
$ vagrant ssh app
vagrant@app:~$ envdir /etc/rf.d/env /opt/app/manage.py test
```

### Ports

The Vagrant configuration maps the following host ports to services running in the virtual machines.

Service                | Port | URL
---------------------- | -----| ------------------------------------------------
Django Web Application | 8000 | [http://localhost:8000](http://localhost:8000)
Graphite Dashboard     | 8080 | [http://localhost:8080](http://localhost:8080)
Kibana Dashboard       | 5601 | [http://localhost:5601](http://localhost:5601)
PostgreSQL             | 5432 | `psql -h localhost`
pgweb                  | 5433 | [http://localhost:5433](http://localhost:5433)
Redis                  | 6379 | `redis-cli -h localhost 6379`
Testem                 | 7357 | [http://localhost:7357](http://localhost:7357)

### Caching

In order to speed up things up, you may want to consider leveraging the `vagrant-cachier` plugin. If installed, it is automatically used by Vagrant.

### Test Mode

In order to run the app in test mode, which simulates the production static asset bundle, reprovision with `VAGRANT_ENV=TEST vagrant provision`.

### Testing

Run all the tests:

```bash
$ ./scripts/test.sh
```

##### Python

To run all the tests on the Django app:

```bash
$ ./scripts/manage.sh test
```

Or just for a specific app:

```bash
$ ./scripts/manage.sh test apps.app_name.tests
```

More info [here](https://docs.djangoproject.com/en/1.8/topics/testing/).

##### JavaScript

When creating new tests or debugging old tests, it may be easier to open the testem page, which polls for changes to the test bundle and updates the test state dynamically.

First, start the testem process.

```bash
$ ./scripts/testem.sh
```
Then view the test runner page at [http://localhost:7357](http://localhost:7357).

To enable livereload, [download the browser extension](http://livereload.com/extensions/)
and start the livereload server with the following command:

```bash
$ ./scripts/npm.sh run livereload
```

#### Bundling static assets

The `bundle.sh` script runs browserify, node-sass, and othe pre-processing
tasks to generate static assets.

The vendor bundle is not created until you run this command with the
`--vendor` flag. This bundle will be very large if combined with `--debug`.

Test bundles are not created unless the `--tests` flag is used.

In general, you should be able to combine `--vendor`, `--tests`, `--debug`,
and `--watch` and have it behave as you would expect.

You can also minify bundles by using the `--minify` flag. This operation is
not fast, and also disables source maps.

The `--list` flag displays module dependencies and does not actually generate
any bundles. It doesn't make sense to combine this with `--watch`.
This flag is for troubleshooting purposes only.

```bash
bundle.sh [OPTION]...

Bundle JS and CSS static assets.

 Options:
  --watch      Listen for file changes
  --debug      Generate source maps
  --minify     Minify bundles (**SLOW**); Disables source maps
  --tests      Generate test bundles
  --list       List browserify dependencies
  --vendor     Generate vendor bundle and copy assets
  -h, --help   Display this help text
```

#### Adding JS dependencies

To add a new JS depenency, update the `JS_DEPS` array in `bundle.sh`, and `package.json` accordingly.
Because our dependencies are shrinkwrapped, follow the [instructions](https://docs.npmjs.com/cli/shrinkwrap#building-shrinkwrapped-packages) for adding a dependency to a shrinkwrapped package.
Rebuild the vendor bundle using `./scripts/bundle.sh --vendor`.
`npm` commands can be run using `./scripts/npm.sh`.

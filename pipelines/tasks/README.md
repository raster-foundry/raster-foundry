Tasks
=====
Creating Tasks
--------------
Tasks are designed to be run using [Luigi](http://luigi.readthedocs.io). Running a development
server via `./scripts/server` will set up a Luigi scheduler that allows running tasks.

### Create task folder
To create a new task, create a folder in this directory to house your task's source code.

### Add docker-compose entry for new task
Next, add an entry to the `docker-compose.yml` file in this directory. It should look something like
this:
```yaml
  test-task:
    image: rf/task-base
    volumes:
      - ./test_task/src:/opt/task/
    working_dir: /opt/task
    entrypoint: python task.py
    networks:
      - scheduler
    environment: # If your tasks access AWS from development
      - AWS_ACCESS_KEY_ID
      - AWS_SECRET_ACCESS_KEY
```

This will of course be slightly different for each task, but the crucial points to note from the
snippet above are:
- The `image` should inherit from rf/task-base or another image that has luigi installed.
- The `networks` key should point to the `scheduler` network; the central scheduler is located on
  the `scheduler` network at the domain name `scheduler.raster-foundry.internal`. This is defined in
  the `docker-compose.yml` file at the project root level.
- The `entrypoint` key should be whatever the base command of your task is. Then you can run
  `docker-compose run <task-name> [ARGS ...]` to test.

### Run scheduler
In order to develop your new task, you will need to run `./scripts/server` from the root project
directory; this will bring up the central scheduler.

### Run task
Finally, from the root project directory, run `./scripts/run-task <task-name> [ARGS ...]`. You can
also run `docker-compose run <task-name> [ARGS ...]` from this folder, that's all the run-task
script does.

### AWS Access
If any of your tasks need AWS access, you will need to place your user credentials in boto-compatible
environment variables in a `.env` file in this directory. This should look something like:
```
AWS_ACCCESS_KEY_ID=<access-key-id-here>
AWS_SECRET_ACCESS_KEY=<secret-access-key-here>
```
You will also need to place those environment variables in the `docker-compose.yml` file as above.

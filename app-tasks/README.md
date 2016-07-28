# Raster Foundry Task/Workflows

Asynchronous tasks and workflows are managed through [Amazon's Simple Workflow Service (SWF)](https://aws.amazon.com/swf/). This `pip` installable python project encapsulates the task running and workflows.

# Requirements

All requirements are handled via the Raster Foundry project's provisioning, including creating a `docker` container to use for development via `./scripts/setup`. 

# Development

Source code for Raster Foundry tasks is in `src/rf/`. The `activities` directory contains synchronous activities that should be run by an activity worker. Activities should be grouped in directories based on their functionality/feature (e.g. upload processing, image export). Similarly, workflows (groups of activities that may need to be strung together) should be grouped by functionality.

Code executed by activities should be broken up into functions for ease of testing as much as possible.

# Testing

Run `./scripts/test` or `./scripts/console app-tasks-decider tox -e tests`

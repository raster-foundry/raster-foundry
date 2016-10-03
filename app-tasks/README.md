# Raster Foundry Task/Workflows

Asynchronous tasks and workflows are managed through [Airflow](https://pythonhosted.org/airflow/index.html).

[DAGs](https://pythonhosted.org/airflow/concepts.html#dags) are stored in the `dags/` directory.
Configuration is stored in `usr/`.
`rf` is a pip-installable package that houses code which can be used an imported in `DAGs`; however, is maintained apart from the `DAGs` to keep it easy to test.

# Requirements

All requirements are handled via the Raster Foundry project's provisioning, including creating a `docker` container to use for development via `./scripts/setup`.

# Development

To enhance `dags` likely requires editing two different sets of files:

 1. Editing/adding an existing `DAG`
 2. Adding functionality to the `rf` library that will be used in the `DAG`

`DAG` code should be as thin as possible, deferring complicated logic and operations to the `rf` library. This will help maintain at least some composability and ensure that tasks performed in `DAGs` can be easily tested. Code executed in the `DAG` should be broken up into functions for ease of testing as much as possible.

# Dags

| Name                      | Purpose                                                                  | Schedule |
|---------------------------|--------------------------------------------------------------------------|----------|
| find\_sentinel2\_scenes   | Finds Sentinel 2 scenes uploaded on execution date and kicks off imports | Daily    |
| import\_sentinel2\_scenes | Imports metadata for a set of sentinel 2 scenes                          | None     |

# Testing

Run `./scripts/test` or `./scripts/console airflow-worker python rf/setup.py test`

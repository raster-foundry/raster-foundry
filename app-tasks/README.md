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
| find\_landsat8\_scenes    | Finds and imports Landsat 8 scenes on execution date                     | Daily    |
| find\_sentinel2\_scenes   | Finds Sentinel 2 scenes uploaded on execution date and kicks off imports | Daily    |
| import\_sentinel2\_scenes | Imports metadata for a set of sentinel 2 scenes                          | None     |

# Common Airflow Commands

## Running DAGs

When the airflow scheduler starts (as part of `./scripts/server` for example), airflow will automatically start backfilling the DAGs it finds back to their start dates if it doesn't have a record of a successful run. This means you shouldn't have to do anything to trigger DAG runs for those circumstances. However, you may want to backfill even farther than the configured start dates for the above DAGs. If that's the case, use the below:

- `./scripts/console airflow-scheduler bash`
- To clear prevoius task runs (necessary if tasks fail or inaccurately report success): `airflow clear [-f] dag name`, e.g., `airflow clear -f find_landsat8_scenes` to clear failed DAG runs for `find_landsat8_scenes` (without the `-f` it would have cleare _all_ runs for `find_landsat8_scenes`).
- For one-off runs: `airflow run dag_id task_id execution_date`, e.g., `airflow run find_landsat8_scenes import_new_landsat8_scenes 2016-03-05` to run the `import_new_landsat8_scenes` task in the `find_landsat8_scenes` DAG for March 5, 2016
- For backfills: `airflow run [-t task_regex] [-s start_date] [-e end_date] dag_id`, e.g., `airflow backfill -t import.* -s 2015-12-13 -e 2015-12-31 find_landsat8_scenes` to run all tasks matching `import.*` from the `find_landsat8_scenes` DAG each of December 13 through December 31, 2016. Note: only DAGs with a `schedule_interval` defined can be backfilled.

DAGs need a `concurrency` value for the `airflow-scheduler` to run more than one at a time. Currently that value is set to 4 in the development config file stored in AWS and defaults to 24 if the environment variable is unavailable.

# Testing

Run `./scripts/test` or `./scripts/console airflow-worker python rf/setup.py test`

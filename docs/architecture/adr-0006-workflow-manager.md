# 0006 - Workflow/Task Manager

## Context

This ADR has been superseded by `ADR-0019`.

Some actions and features of Raster Foundry require a way to manage asynchronous tasks and workflows. 
For instance, user uploads of imagery or tools may start workflows in an ad hoc manner, while in
contrast imports of imagery from NASA or partners may need to happen on a schedule. The nature of
these tasks could vary from bash scripts and python functions to spark jobs and ECS tasks.

The ideal tool will provide some means to monitor task progress, retry on some failures, and 
notify personnel if necessary. There are a few options of tools we can use: celery, SWF, Luigi, and Airflow.
Azavea has experience working with both celery and SWF; however, due to our past experience with these
tools it seemed prudent to explore additional options as well.

| Workflow Tool   | Pros | Cons |
|-----------------|------|------|
| Celery          | Familiar, written in python, flexible | Provides poor primitives for workflows, many open issues, difficult to monitor workflows |
| SWF (botoflow)) | Familiar, now written in python, maintaining state is not our responsibility (HA by default), great primitives for workflows and tasks | Difficult to monitor, relatively immature tools and projects, not many others using it |
| Luigi           | Mature, seems to be stable, written in python | Unfamiliar execution model, primarily designed for scheduled, recurring task |
| Airflow         | Mature, stable, fits into our execution model, written in python, excellent UI | Requires celery (for what we want to do)), requires managing the scheduler and a cache |
## Decision

Raster Foundry will use Airflow as a task manager. There are a number of advantages over some of the
alternatives. First, Airflow has a large, active, user base that have used it in production. The 
project itself is in the Apache incubator, providing a strong signal that the project is of high quality
Second, Airflow's UI for monitoring task and workflow progress is great. It provides 
high-level relevant information that will enable us to diagnose issues quickly. Additionally, it
provides a means to view log output of tasks directly in the admin interface. Third, Airflow 
supports both scheduled and ad hoc tasks. Lastly, Airflow's architecture would re-use many 
components that will already be a part of Raster Foundry's infrastructure - including a Postgres
database and a redis cache.

## Consequences and Alternatives

As a result of choosing Airflow we will need to become more familiar with it as a tool. However,
the documentation is good and always getting better. Additionally, we may need to extend its 
functionality if it is lacking in some area; however, that is unlikely to be the case in the
short-term.

While ideal in many aspects, there are a few areas that Airflow does not necessarily provide the
greatest of tools out of the box. For instance, it is possible to approach versioning of 
tasks/workflows from a few  different ways, but there is not an obviously best way. Because tasks will be
run inside the container of the worker itself, we will need to do one of the following:
 - ensure that all dependencies for all  tasks are installed in that container
 - write an Airflow operator that can run ECS task definitions
 - use queues to manage which workers take which jobs

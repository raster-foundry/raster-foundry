# 0019 - Workflow/Task Manager

## Context

This ADR supersedes `ADR-006`.

Back in August of 2016, we went through an extensive evaluation process for a workflow manager. After looking at a number of open source and hosted solutions, we settled on Apache Airflow. Since then, we've had some success with Airflow, but also a number of setbacks.

First, we've encountered a number of deeply rooted issues with Airflow's scheduler. At times it would lock up and cease scheduling queued jobs, emitting cryptic error messages in the process. Other times, it would consume a large percentage of an instance's CPU while emitting many valueless log messages.

Second, Airflow's UI was a big differentiator in our initial decision making process. In practice, the UI proved confusing to use, and hasn't lended itself well to running in a distributed environment (e.g., task log centralization, real-time log reporting).

Lastly, our troubleshooting processes often led us to community supported help resources, like the Airflow mailing lists and issues. The more time we spent reading through these entries, the lower our confidence in Airflow became. Leadership was in a constant state of turmoil around releases, and a presence of production grade users became less and less visible.

## Decision

We are replacing all of the following Airflow components with AWS Batch:

- Scheduler
- Web server
- Worker
- Executor (Celery)

AWS Batch is a hosted service produced by Amazon Web Services (AWS). When used in `MANAGED` mode, it assists in scheduling, executing, and reporting status on submitted jobs. In addition, it provides a minimal UI for visually tracking job status. Under the covers, its execution environment is Amazon ECS.

## Consequences

- By running Airflow in production for almost a year, we've gained a signifiant amount of expertise in troubleshooting issues associated with it, and the tasks it executes for us. A portion of this expertise will be lost, and will need to be rebuilt for AWS Batch.
- AWS Batch aggregates its logs in CloudWatch Logs. The is a departure from our approach of centralizing logs in Papertrail.
- The Airflow migration to AWS Batch has consumed, and will continue to consume engineering resources. Those resources could have been spent in other areas of the product.
- Because AWS Batch is a hosted service, depending on it makes it harder to make Raster Foundry a platform that can be deployed on-premise.
- We will also have to engineering a solution for enabling local development because no mechanisms exist to simulate AWS Batch locally.
- AWS Batch has no support for scheduling jobs on a recurring schedule (e.g., nightly jobs). This support will have to be engineered outside of the hosted service.

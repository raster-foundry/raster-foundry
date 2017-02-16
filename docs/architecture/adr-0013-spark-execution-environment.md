# 0013 - Spark Execution Environment

## Context

Part of the Raster Foundry processing workflow requires a source imagery ingest into a GeoTrellis Catalog. GeoTrellis depends on Apache Spark to parallelize work during an ingest, and that work typically occurs on an Apache Spark enabled compute cluster.

Prior iterations of Raster Foundry attempted ingests with an Apache YARN managed compute cluster via Amazon Elastic MapReduce (EMR) on a per-request basis. Unfortunately, that didn't perform well due to the overhead of bootstrapping an EMR cluster.

Our goal for the this iteration aims to keep some Spark cluster components active at all times through a combination of spot pricing and usage based cluster auto-scaling. This approach should help minimize cluster bootstrapping durations and keep cost-incurring cluster resources at a minimum.

## Decision

Given that we are still early in Raster Foundry's product development, the desire to keep costs low and ship quickly carry significant weight. In order to meet those objectives, but still minimize cluster bootstrapping overhead, the first pass at having Spark enabled resources on standby will consist of a shared (across staging and production) Amazon EMR cluster.

The overall Raster Foundry infrastructure will be split across three Amazon Virtual Private Clouds (VPCs):

- Staging (`10.0.0.0/18`, 16382 IPs)
- Spark (`10.0.64.0/18`, 16382 IPs)
- Production (`10.0.128.0/18`, 16382 IPs)

The `Staging` and `Production` VPCs will be peered with the `Spark` VPC to allow direct private network access, if necessary. Regardless, public facing APIs such as the Amazon EMR's Step API, or the Raster Foundry API can be used to pass information between systems.

Despite having thought long and hard about the consequences, I suspect that the section below does not cover all of the issues that lie ahead of us. The hope is that at least these core ideas remain in future iterations of the Spark execution environment:

- Shared cluster resources between environments
- Isolated environments at the VPC level
- Build on top of the Amazon EMR platform
- Use spot pricing and auto-scaling aggressively to keep costs low

## Consequences

- All of our current VPCs were configured with the `10.0.0.0/16` CIDR. In order to modify the CIDR ranges, almost all resources housed within the VPC will need to be destroyed and recreated.

- The EMR `Master` instance group requires an on-demand instance, and that instance must be from the `M4`, `C4`, `R3`, `I2`, or `D2` instance families. Reserved Instances will have to be considered to keep costs low.

- Terraform does not yet have complete support for Amazon EMR (some basic support was added in October). In order to take advantage of cost-cutting features like spot bidding (Spot Fleet support hinted at during re:Invent 2016) and auto-scaling for `Core` and `Task` instance groups, we'll need to lean on CloudFormation or direct access to the AWS EMR API.

- It is recommended that auto-scaling of the `Core` and `Task` instances be done against cluster-level YARN metrics published to CloudWatch. We'll need to sort out the sweet spot for automatically scaling up and down given our workloads.

- It is not year clear what the best EMR job submission method is. The Step API is desirable because it integrates well with existing Airflow operators and avoids a number of security group and service discovery issues.

- Direct `spark-submit` invocations from EC2 nodes can be brittle and require some amount of undesirable maneuvering (configuration file copying, security group access) to get working. Also, it lacks a built-in job tracking mechanism provided by the step API.

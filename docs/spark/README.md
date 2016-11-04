# Apache Spark Usage within Raster Foundry

Apache Spark is a cluster computing system with a Scala API for defining execution graphs and an engine for executing them across multiple machines. GeoTrellis is a library built on top of Spark for describing geospatial transformations against raster data sets.

Raster Foundry uses both to mosaic multiple large raster data sets and output the result in a way that can be consumed by a web UI.

## Table of Contents

* [Spark Components](#spark-components)
  * [Master](#master)
  * [Worker](#workers-and-executors)
  * [Driver](#driver)
* [Master Recovery](#master-recovery)
* [Development Environment](#development-environment)
  * [Building a Job JAR](#building-a-job-jar)
  * [Local Spark Standalone Cluster](#local-spark-standalone-cluster)
* [Safety Testing](#safety-testing)

## Spark Components

Often times Spark is deployed on top of a cluster resource manager, such as Apache Mesos or Apache Hadoop YARN (Yet Another Resource Negotiator). In addition to those options, Spark comes bundled with **Spark Standalone**, which is a built-in way to run Spark in a clustered environment.

A Spark Standalone cluster has the following components:

### Driver

A Spark Standalone driver prepares the Spark context and submits an execution graph to the **master**. As progress is made against the jobs, the driver also helps coordinate across job stages.

### Master

The Spark Standalone master provides an RPC endpoint for **drivers** and **workers** to communicate with. It also creates tasks out of a job's execution graph and submits them to **workers**.

There is generally only one master in a Spark Standalone cluster, but when multiple master are up at the same time one must be elected the leader.

### Workers and Executors

Workers within a Spark Standalone cluster interact with the **master** to determine what jobs they can do. Within a **worker** exist **executors**, and executors work on tasks within the jobs assigned to a worker. They also communicate task status back to the **driver**.

A Spark Standalone cluster can have one or more workers within a cluster, and each worker can have one or more executors (generally limited to the number of CPUs available on the worker).

## Master Recovery

A Spark Standalone **master** has several supported `spark.deploy.recoveryMode` options. Within the local development environment `FILESYSTEM` is used. This mode instructs the master to log information about **workers** to the file system (`spark.deploy.recoveryDirectory`) when they register. The information logged allows the master to recover from a failure given that the `recoveryDirectory` is still intact.

## Development Environment

The local development environment for Raster Foundry contains a Docker Compose configuration for running a Spark Standalone cluster. All services within the Docker Compose configuration share a common base image (only the **master** builds on that slightly to prepare the cluster state directory for recovery).

**Note**: It is recommended that you execute the commands in each of the sections below in separate terminal windows so that you can inspect the output interactively as the state of the cluster changes.

### Building a Job JAR

Jobs are submitted to a Spark Standalone cluster as a JAR file. In order to exercise the cluster, this repository contains a `SparkPi` job that can be compiled into a JAR with:

```bash
$ docker-compose \
    -f docker-compose.spark.yml run --rm --no-deps --user root --entrypoint sbt \
    spark-driver package
```

That process will produce `rf-worker_2.11-0.1.0.jar` inside of the `target/scala-2.11` directory.

### Local Spark Standalone Cluster

To get the Spark Standalone environment up-and-running, start by bring up the Spark **master**. It provides a web console on port `8888`:

```bash
$ docker-compose -f docker-compose.spark.yml up spark-master
Starting rasterfoundry_spark-master_1
Attaching to rasterfoundry_spark-master_1
spark-master_1  | Using Spark's default log4j profile: org/apache/spark/log4j-defaults.properties
spark-master_1  | 16/08/22 19:25:03 INFO Master: Registered signal handlers for [TERM, HUP, INT]
spark-master_1  | 16/08/22 19:25:04 WARN NativeCodeLoader: Unable to load native-hadoop library for your platform... using builtin-java classes where applicable
spark-master_1  | 16/08/22 19:25:04 INFO SecurityManager: Changing view acls to: spark
spark-master_1  | 16/08/22 19:25:04 INFO SecurityManager: Changing modify acls to: spark
spark-master_1  | 16/08/22 19:25:04 INFO SecurityManager: SecurityManager: authentication disabled; ui acls disabled; users with view permissions: Set(spark); users with modify permissions: Set(spark)
spark-master_1  | 16/08/22 19:25:04 INFO Utils: Successfully started service 'sparkMaster' on port 7077.
spark-master_1  | 16/08/22 19:25:04 INFO Master: Starting Spark master at spark://172.18.0.4:7077
spark-master_1  | 16/08/22 19:25:04 INFO Master: Running Spark version 1.6.2
spark-master_1  | 16/08/22 19:25:04 INFO Utils: Successfully started service 'MasterUI' on port 8080.
spark-master_1  | 16/08/22 19:25:04 INFO MasterWebUI: Started MasterWebUI at http://172.18.0.4:8080
spark-master_1  | 16/08/22 19:25:04 INFO Utils: Successfully started service on port 6066.
spark-master_1  | 16/08/22 19:25:04 INFO StandaloneRestServer: Started REST server for submitting applications on port 6066
spark-master_1  | 16/08/22 19:25:04 INFO FileSystemRecoveryModeFactory: Persisting recovery state to directory: /spark-state
spark-master_1  | 16/08/22 19:25:05 INFO Master: I have been elected leader! New state: ALIVE
```

After that, bring up the Spark worker:

```bash
$ docker-compose -f docker-compose.spark.yml up spark-worker
rasterfoundry_spark-master_1 is up-to-date
Recreating rasterfoundry_spark-worker_1
Attaching to rasterfoundry_spark-worker_1
spark-worker_1  | Using Spark's default log4j profile: org/apache/spark/log4j-defaults.properties
spark-worker_1  | 16/08/22 19:28:02 INFO Worker: Registered signal handlers for [TERM, HUP, INT]
spark-worker_1  | 16/08/22 19:28:02 WARN NativeCodeLoader: Unable to load native-hadoop library for your platform... using builtin-java classes where applicable
spark-worker_1  | 16/08/22 19:28:02 INFO SecurityManager: Changing view acls to: spark
spark-worker_1  | 16/08/22 19:28:02 INFO SecurityManager: Changing modify acls to: spark
spark-worker_1  | 16/08/22 19:28:02 INFO SecurityManager: SecurityManager: authentication disabled; ui acls disabled; users with view permissions: Set(spark); users with modify permissions: Set(spark)
spark-worker_1  | 16/08/22 19:28:03 INFO Utils: Successfully started service 'sparkWorker' on port 44298.
spark-worker_1  | 16/08/22 19:28:03 INFO Worker: Starting Spark worker 172.18.0.5:44298 with 2 cores, 512.0 MB RAM
spark-worker_1  | 16/08/22 19:28:03 INFO Worker: Running Spark version 1.6.2
spark-worker_1  | 16/08/22 19:28:03 INFO Worker: Spark home: /usr/lib/spark
spark-worker_1  | 16/08/22 19:28:03 INFO Utils: Successfully started service 'WorkerUI' on port 8081.
spark-worker_1  | 16/08/22 19:28:03 INFO WorkerWebUI: Started WorkerWebUI at http://172.18.0.5:8081
spark-worker_1  | 16/08/22 19:28:03 INFO Worker: Connecting to master spark.services.rasterfoundry.internal:7077...
spark-worker_1  | 16/08/22 19:28:03 INFO Worker: Successfully registered with master spark://172.18.0.4:7077
```

Lastly, submit the `SparkPi` JAR created in the section above to the cluster:

```bash
$ docker-compose \
    -f docker-compose.spark.yml run --rm -p 4040:4040 \
    spark-driver \
        --class "com.rasterfoundry.worker.SparkPi" \
        --master spark://spark.services.rasterfoundry.internal:7077 \
        target/scala-2.11/rf-worker_2.11-0.1.0.jar 1000
```

## Safety Testing

Using the development environment described above, each Spark Standalone component was terminated using `SIGINT` in an attempt to simulate hard failures. Below are a collection of notes on how the Spark Standalone cluster behaved after each component termination.

<table>
    <tbody>
        <tr>
            <th>Component</th>
            <th>Application Status</th>
            <th>Notes</th>
        </tr>
        <tr>
            <td><b>Master</b></td>
            <td><code>FINISHED</code></td>
            <td>
                <ul>
                    <li><b>Worker</b> re-registered with <b>master</b></li>
                    <li>Application re-registered with <b>master</b></li>
                </ul>
            </td>
        </tr>
        <tr>
            <td><b>Worker</b></td>
            <td><code>FINISHED</code></td>
            <td>
                <ul>
                    <li><b>Master</b> told application that <b>executor</b> was lost</li>
                    <li><b>Worker</b> re-registered with <b>master</b>; replacement <b>executor</b> launched</li>
                </ul>
            </td>
        </tr>
        <tr>
            <td><b>Driver</b></td>
            <td><code>FAILED</code></td>
            <td>
                <ul>
                    <li><b>Master</b> received unregister request from application</li>
                    <li><b>Worker</b> killed <b>executor</b> that was executing tasks</li>
                    <li><b>Worker</b> disassociation with task propagated to <b>master</b></li>
                </ul>
            </td>
        </tr>
    </tbody>
</table>

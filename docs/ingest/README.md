# Raster Foundry Tile Ingests

In Raster Foundry imagery is ingested through a Spark job that collects
together potentially many different sources and attempts to produce a
single tile with multiple bands. The ingest process consumes a block of
JSON which specifies the details of exactly how a given ingest should
occur. Further detail can be found below.

## Table of Contents

* [The Ingest Process](#the-ingest-process)
  * [Building the jar](#building-the-jar)
  * [Running an Ingest](#running-an-ingest)
* [Example JSON](#example-json)

## The Ingest Process

Each ingest is kicked off with `spark-submit` rather than `java -jar` so
that all jars necessary for distributed processing are provided at
runtime. This means, however, that a working Spark installation must be
available wherever the process is to be run.

### Building the Jar

Through docker, the environment needed to build and run the
ingest are provided. To build the ingest jar, simply run
`scripts/cibuild` or directly call
`docker-compose run --no-deps --rm --entrypoint ./sbt api-server ingest/assembly`.

### Running an Ingest

Once the ingest jar has been built, the ingest can be run locally in a
couple of different ways. In the demonstration that follows, we'll use
`localJob.json` which attempts a filesystem based ingest - both source
and destination - but which requires no special credentials and runs
fairly quickly due to the limited size of the source tiles.

A one-off local ingest which attempts to carry out the entire ingest job
inside the driver (rather than serializing portions of the program for
execution elsewhere) by calling:

```bash
docker-compose -f docker-compose.spark.yml run spark-driver \
               --driver-memory 2G \
               /opt/rf/jars/rf-ingest.jar -t -j file:///opt/rf/test-resources/localJob.json
```

To carry out a more complex (and production-like) ingest, multiple
docker containers can be used to simulate a real, distributed cluster.
First, we'll need to ensure that the cluster is running and is ready to
take jobs. Note the docker-compose file selection. We must do this to
ensure that Spark is avalaible.

```bash
docker-compose -f docker-compose.spark.yml up spark-master spark-worker
```

At this point, a Spark driver (spark-master) and a Spark executor
(spark-worker) should be running in their respective containers.
Finally, we kick off an ingest. Note that we specify the master
address - we can do this because docker's virtual network willuprovide
name resolution according to the config in `docker-compose.spark.yml`.

```bash
docker-compose -f docker-compose.spark.yml run spark-driver \
               --master spark://spark.services.rasterfoundry.internal:7077 \
               --driver-memory 2G \
               --executor-memory 2G \
               /opt/rf/jars/rf-ingest.jar -t -j file:///opt/rf/test-resources/localJob.json
```

To run the slower S3 based ingest (input and output) defined in
`awsJob.json` within the ingest subproject, `r/localJob/awsJob/g` the
above and consider boosting the available memory on driver and executor
above `2G`. Larger jobs will almost certainly require more memory than
is necessary for the ingest described above.

## Example JSON

As mentioned above, each ingest begins its life as a bit of JSON. Below is
a `jsdoc` style annotation of one of the sample definitions provided [within
the ingest subproject](../../app-backend/ingest/src/test/resources/awsJob.json):

```javascript
/** Ingest Specification */
{
    /**
      * Ingest ID (Java UUID)
      * @type String
      */
    "id": "dda6080f-f7ad-455d-b409-764dd8c57039",
    /** List of Ingest Layers */
    "layers": [{
        /**
          * Ingest Layer ID (Java UUID)
          * @type String
          */
        "id": "8436f7e9-b7f7-4d4f-bda8-76b32c356dff",
        /** Layer Output Definition */
        "output": {
            /**
              * NoData Pattern
              * @type Object
              */
            "ndPattern": {
                /**
                  * Band/cell value pattern which specifies NoData cells
                  * @type Map[Number, Number] (the key is serialized as a string by JSON convention)
                  */
                "pattern": { "1": 3.3, "2": 5.3 }
            },
            /**
              * Output Layer URI
              * @type String
              */
            "uri": "s3://rasterfoundry-staging-catalogs-us-east-1/test",
            /**
              * Output Layer CellType
              * @type String
              */
            "cellType": "uint16raw",
            /**
              * Output Histogram Bin Count
              * @type Number
              */
            "histogramBuckets": 512,
            /**
              * Size of output tiles
              * @type Number
              */
            "tileSize": 256,
            /**
              * GeoTrellis resample method to be used
              * @type String
              */
            "resampleMethod": "NearestNeighbor",
            /**
              * GeoTrellis method for indexing keys
              * @type String
              */
            "keyIndexMethod": "ZCurveKeyIndexMethod",
            /**
              * Whether or not to pyramid
              * @type Boolean
              */
            "pyramid": true,
            /**
              * Whether or not to save the native resolution
              *  (not yet implemented)
              *
              * @type Boolean
              */
            "native": true
        },
        /** List of Layer Input Sources */
        "sources": [{
            /**
              * Input URI
              * @type String
              */
            "uri": "http://landsat-pds.s3.amazonaws.com/L8/107/035/LC81070352015218LGN00/LC81070352015218LGN00_B4.TIF",
            /**
              * Extent of Input
              * @type Array of Numbers
              */
            "extent": [138.8339238,  34.9569460, 141.4502449,  37.1094577],
            /**
              * Source extent CRS (projection identification)
              * @type String
              */
            "crsExtent": "epsg:32654",
            /**
              * Source CRS (projection identification)
              * @type String
              */
            "crs": "epsg:3857",
            /** Source Cell Size */
            "cellSize": {
                /**
                  * Cell Size Width
                  * @type Number
                  */
                "width": 37.151447651062888,
                /**
                  * Cell Size Height
                  * @type Number
                  */
                "height": -37.151447651062888
            },
            /** Mapping from source image to target image band */
            "bandMaps": [
                {
                    /**
                      * Source (input) band
                      * @type Number
                      */
                    "source": 1,
                    /**
                      * Target (output) band
                      * @type Number
                      */
                    "target": 1
                 }]
        }, /** Truncated for brevity */]
    }]
}
```


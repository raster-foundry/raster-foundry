package com.azavea.rf.ingest

import com.azavea.rf.datamodel._
import org.apache.spark._
import org.apache.spark.rdd._

import java.util.UUID
import java.net.URI
import java.sql.Timestamp

object Ingest extends SparkBoilerplate {
  // The task definition for each separate unit of work within Spark
  case class Task(name: String, id: UUID, sceneName: String, sceneId: UUID, sourceUri: URI)

	def main(args: Array[String]): Unit = {

		val image = Image(UUID.randomUUID(), new Timestamp(23), new Timestamp(4312), UUID.randomUUID(), "creator", "modifier", 9,
			Visibility.Public, URI.create("s3://geotrellis-test/nlcd-geotiff/"), UUID.randomUUID(), List(), Map()
		)

		val scene1 = Scene.WithRelated(
			UUID.randomUUID(), new Timestamp(123), "creator", new Timestamp(1234), "modifier",
			UUID.randomUUID(), Visibility.Public, 0.1F, List(), Map(), None, None, JobStatus.Queued,
			JobStatus.Queued, JobStatus.Queued, None, None, "scene1", Seq(image), None, Seq()
		)
		val scene2 = scene1.copy(name = "scene2")

		val ingestDefinition: IngestDefinition = IngestDefinition("ingestDef", UUID.randomUUID(), Array(scene1, scene2))

		// Break ingest definition up into tasks
		val tasks: RDD[Task] = sc.parallelize(ingestDefinition.decomposeTasks)

		println(tasks.count())
	}
}

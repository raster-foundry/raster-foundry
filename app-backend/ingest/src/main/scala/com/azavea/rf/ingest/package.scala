package com.azavea.rf

import com.azavea.rf.datamodel.IngestDefinition

import java.net.URI

package object ingest {
  implicit class PrepIngestDefinition(val self: IngestDefinition) extends IngestPrep[IngestDefinition] {
    def decomposeTasks = for {
      scene <- self.scenes
      image <- scene.images
      uri <- image.sourceUri.findResources()
      ingestId = self.id
      sceneName = scene.name
      sceneId = scene.id
    } yield Ingest.Task(self.name, ingestId, sceneName, sceneId, image.sourceUri)
  }

  implicit class UriMethods(val self: URI) {
    def findResources(ext: Option[String] = None): Array[URI] = self.getScheme.toUpperCase match {
      case "S3" => s3.Util.listKeys(self.toString, "", true)
      case _    => throw new Exception("Unsupported URI scheme: ${self}")
    }
  }
}

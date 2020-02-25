package com.rasterfoundry.batch.groundwork

import java.util.UUID

class CreateTaskGrid(annotationProjectId: UUID) {
  println(annotationProjectId)
  // assuming upload has been processed, there is:
  //
  // - a scene
  // - in a layer
  // - in a project
  // - for this annotation project
  //
  // we want to:
  //
  // - get its footprint
  // - create a TaskGridFeatureCreate with the footprint
  // - also with the sizeMeters that corresponds to the taskSizePixels of this annotation project
  // - use the existing TaskDao method to insert the task grid
  // - update annotation project aoi with the footprint
  // - mark the annotation project ready
}

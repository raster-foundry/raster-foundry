package com.rasterfoundry.batch.stacExport.v2

import com.rasterfoundry.datamodel.AnnotationProject

import cats.syntax.apply._

object README {

  private type LabelItemMap =
    Map[newtypes.AnnotationProjectId, newtypes.LabelItem]
  private type ImageryItemMap =
    Map[newtypes.AnnotationProjectId, newtypes.SceneItem]

  private val header = List(
    "| Image Name | Labels Path | Imagery Path |",
    "| :--------- | ----------- | ------------ |"
  )

  private def renderRow(
      annotationProject: AnnotationProject,
      labelItemMap: LabelItemMap,
      imageryItemMap: ImageryItemMap
  ): Option[String] = {
    (
      labelItemMap.get(newtypes.AnnotationProjectId(annotationProject.id)),
      imageryItemMap.get(newtypes.AnnotationProjectId(annotationProject.id))
    ).mapN {
      case (labelItem, imageryItem) =>
        val labelItemPath =
          s"labels/${labelItem.value.id}.json"
        val imageryItemPath =
          s"images/${imageryItem.value.id}.json"
        s"| ${annotationProject.name} | $labelItemPath | $imageryItemPath |"
    }

  }

  private def renderTable(
      annotationProjects: List[AnnotationProject],
      labelItemMap: LabelItemMap,
      imageryItemMap: ImageryItemMap
  ): String =
    (header ++ (annotationProjects flatMap { proj =>
      renderRow(proj, labelItemMap, imageryItemMap)
    })).mkString("\n")

  def render(
      annotationProjects: List[AnnotationProject],
      labelItemMap: LabelItemMap,
      imageryItemMap: ImageryItemMap
  ): String =
    s"""# GroundWork STAC Export

This directory contains a [STAC](https://stacspec.org/) export of data from GroundWork.

It's organized into two separate STAC [collections](https://github.com/radiantearth/stac-spec/blob/master/collection-spec/collection-spec.md), one for imagery and one for labels.

The collections each contain an [item](https://github.com/radiantearth/stac-spec/blob/master/item-spec/item-spec.md)
for every image in the GroundWork campaign that you created this export for.

${renderTable(annotationProjects, labelItemMap, imageryItemMap)}

Imagery items in this export contain [TMS](https://en.wikipedia.org/wiki/Tile_Map_Service) URLs. You can put those
TMS URLs into tools like [geojson.io](http://geojson.io/#map=2/20.0/0.0) (via "Meta" -> "Add map layer") or QGIS (via
"XYZ Tiles") to view them.

Label items in this export contain a `data` asset pointing to a GeoJSON file of the labels
that they contain. You can view this GeoJSON file in QGIS by dragging it into the workspace.
They also include links to their imagery -- you can find the imagery link for a label item in the
array element of its `links` property with the property `"rel": "source"`. That link refers to the imagery item with
the TMS URL(s) that match what was visible in GroundWork while these labels were being created.

The ecosystem for consuming STACs for different purposes is to this point a bit underdeveloped. Some tools you might
be interested in are:

- [Franklin](https://github.com/azavea/franklin) for bringing up a web server for your STAC
- [PySTAC](https://github.com/stac-utils/pystac) for reading and manipulating the STAC

If there's something specific you'd like to be able to do with your STAC export or that you've done that you'd like
to tell us about, please contact us with the chat in GroundWork or [by email](mailto:groundwork@azavea.com).
"""

}

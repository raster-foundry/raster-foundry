from typing import List
from shapely.geometry import Polygon

from rastervision.core.box import Box
from rastervision.core.data import (
    ClassConfig, CRSTransformer, GeoJSONVectorSourceConfig, RasterioSource,
    RasterizedSource, Scene, SemanticSegmentationLabelSource)


def make_scene(scene_id: str, class_config: ClassConfig, img_info: dict,
               labels_uri: str, aoi_polygons: List[Polygon]) -> Scene:
    raster_source = make_raster_source(img_info)

    label_source = make_label_source(
        labels_uri=labels_uri,
        class_config=class_config,
        extent=raster_source.get_extent(),
        crs_transformer=raster_source.get_crs_transformer())

    scene = Scene(
        id=scene_id,
        raster_source=raster_source,
        ground_truth_label_source=label_source,
        aoi_polygons=aoi_polygons)
    return scene


def make_raster_source(img_info: dict) -> RasterioSource:
    img_uri = img_info['ingestLocation']
    if not isinstance(img_uri, list):
        img_uri = [img_uri]
    raster_source = RasterioSource(uris=img_uri, channel_order=[0, 1, 2])
    return raster_source


def make_label_source(
        labels_uri: str, class_config: ClassConfig, extent: Box,
        crs_transformer: CRSTransformer) -> SemanticSegmentationLabelSource:
    geojson_cfg = GeoJSONVectorSourceConfig(
        uri=labels_uri, default_class_id=1, ignore_crs_field=True)
    vector_source = geojson_cfg.build(class_config, crs_transformer)

    label_source = SemanticSegmentationLabelSource(
        raster_source=RasterizedSource(
            vector_source=vector_source,
            background_class_id=0,
            extent=extent,
            crs_transformer=crs_transformer),
        null_class_id=0)
    return label_source

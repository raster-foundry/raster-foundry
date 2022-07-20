from typing import List
from shapely.geometry import Polygon, mapping, shape

from rastervision.core.box import Box
from rastervision.core.data import (
    ClassConfig, CRSTransformer, GeoJSONVectorSourceConfig, RasterioSource,
    RasterizedSource, Scene, SemanticSegmentationLabelSource,
    transform_geojson, geometries_to_geojson)


def make_scene(scene_id: str, class_config: ClassConfig, img_info: dict,
               labels_uri: str, aoi_polygons: List[Polygon]) -> Scene:
    raster_source = make_raster_source(img_info)
    extent = raster_source.get_extent()
    crs_transformer = raster_source.get_crs_transformer()

    label_source = make_label_source(
        labels_uri=labels_uri,
        class_config=class_config,
        extent=extent,
        crs_transformer=crs_transformer)

    # transform AOI to pixel coords
    aoi_polygons = aoi_to_pixel_coords(aoi_polygons, crs_transformer)

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


def aoi_to_pixel_coords(aoi_polygons: List[Polygon],
                        crs_transformer: CRSTransformer) -> List[Polygon]:
    """Transform AOI to pixel coordinates"""
    polygon_geojsons = [mapping(p) for p in aoi_polygons]
    full_geojson = geometries_to_geojson(polygon_geojsons)
    transformed_geojson = transform_geojson(full_geojson, crs_transformer)
    transformed_polygons = [
        shape(f['geometry']) for f in transformed_geojson['features']
    ]
    return transformed_polygons

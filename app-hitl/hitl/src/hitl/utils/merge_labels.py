import geopandas as gpd


def merge_labels_with_task_grid(labels: gpd.GeoDataFrame, task_grid: gpd.GeoDataFrame) -> gpd.GeoDataFrame:
    return gpd.overlay(labels, task_grid, how='intersection', keep_geom_type=True)

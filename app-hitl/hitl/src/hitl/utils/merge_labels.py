import geopandas as gpd


def merge_labels_with_task_grid(labels: gpd.GeoDataFrame, task_grid: gpd.GeoDataFrame) -> gpd.GeoDataFrame:
    tasks = task_grid[['id', 'geometry']].rename(columns={'id': 'taskId'})
    return gpd.overlay(labels, tasks, how='intersection', keep_geom_type=True)

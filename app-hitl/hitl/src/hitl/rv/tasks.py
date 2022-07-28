from typing import List

import geopandas as gpd
from shapely.geometry import Polygon


def get_labeled_tasks(task_grid: gpd.GeoDataFrame) -> List[Polygon]:
    return task_grid[task_grid.status == 'VALIDATED'].geometry.to_list()

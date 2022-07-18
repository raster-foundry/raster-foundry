import numpy as np
import geopandas as gpd
from shapely.geometry import Polygon

from tqdm.auto import tqdm

from rastervision.core.box import Box
from rastervision.core.data import (RasterioSource,
                                    SemanticSegmentationSmoothLabels)


def entropy(x: np.ndarray) -> np.ndarray:
    out = -x * np.log(x)
    out[x == 0] = 0.
    return out


def compute_priority_scores(task_grid: gpd.GeoDataFrame,
                            labels: SemanticSegmentationSmoothLabels,
                            raster_source: RasterioSource) -> gpd.GeoDataFrame:
    def task_polygon_to_score(task_polygon: Polygon) -> float:
        window_map_coords = Box.from_shapely(task_polygon)
        window_pixel_coords = raster_source.get_transformed_window(
            window_map_coords, inverse=True)
        pixel_probs = labels[window_pixel_coords]
        pixel_entropies = (
            (entropy(pixel_probs).sum(axis=0)) / np.log(pixel_probs.shape[0]))
        score = pixel_entropies.mean()
        return score

    with tqdm(task_grid.geometry, desc='Computing task scores') as bar:
        task_grid.loc[:, 'score'] = [
            task_polygon_to_score(task_polygon) for task_polygon in bar
        ]

    return task_grid

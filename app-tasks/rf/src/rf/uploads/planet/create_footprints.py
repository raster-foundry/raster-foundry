def bbox_from_planet_feature(planet_feature):
    """Extract the tile footprint from a planet feature

    Args:
        planet_feature (dict): planet feature to extract bbox from

    Returns:
        list: coords of the feature's bbox
    """

    # it's a geojson polygon, so drill in
    coords = planet_feature['geometry']['coordinates'][0]
    xs = [p[0] for p in coords]
    ys = [p[1] for p in coords]
    min_x = min(xs)
    max_x = max(xs)
    min_y = min(ys)
    max_y = max(ys)

    return [[[[min_x, min_y],
              [max_x, min_y],
              [max_x, max_y],
              [min_x, max_y],
              [min_x, min_y]]]]

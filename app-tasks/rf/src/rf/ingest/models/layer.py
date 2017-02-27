""" Python class to represent a layer within an ingest """


class Layer(object):
    """Construct layer to ingest"""

    def __init__(self, id, output_uri, sources, cell_size, crs="epsg:3857", pyramid=True, native=False,
                 cell_type="uint16raw", histogram_buckets=512, tile_size=256,
                 resample_method="NearestNeighbor", key_index_method="ZCurveKeyIndexMethod",
                 ingest_resolution_meters=None):
        """
            Create a new ingest Layer

            Args:
                id (str): scene id layer is based on
                output_uri (str): Output layer URI
                sources (list[dict]): list of sources that comprise layer
                cell_size (dict): height and width of cells in layer
                crs (str): Output layer CRS
                pyramid (bool): Whether or not to pyramid
                native (bool): Whether or not to save native resolution
                cell_type (bool): Output layer cell-type
                histogram_buckets (int): Output histogram bin count
                tile_size (int): Size of output tiles
                resample_method (str): GeoTrellis resample method
                key_index_method (str): GeoTrellis method for indexing keys
                ingest_resolution_meters (float): Optional resolution that will dictate which images
                    from the scene are used
        """

        self.id = id
        self.sources = sources
        self.output_uri = output_uri
        self.cell_size = cell_size
        self.tile_size = tile_size
        self.crs = crs
        self.output_pyramid = pyramid
        self.output_native = native
        self.output_cell_type = cell_type
        self.output_histogram_buckets = histogram_buckets
        self.output_tile_size = tile_size
        self.output_resample_method = resample_method
        self.output_key_index_method = key_index_method
        self.ingest_resolution_meters = ingest_resolution_meters

    def to_dict(self):
        """ Return a dict formatted specifically for serialization to an ingest definition component """

        return {
            'id': self.id,
            'output': {
                'uri': self.output_uri,
                'crs': self.crs,
                'cellType': self.output_cell_type,
                'tileSize': self.tile_size,
                'resampleMethod': self.output_resample_method,
                'keyIndexMethod': self.output_key_index_method,
                'histogramBuckets': self.output_histogram_buckets,
                'cell_size': self.cell_size,
                'pyramid': self.output_pyramid,
                'native': self.output_native
            },
            'sources': [s.to_dict() for s in self.sources]
        }

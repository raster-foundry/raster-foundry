""" Python class to represent a layer within an ingest """

from .source import Source


class Layer(object):
    def __init__(self, scene,
                 output_uri, output_crs="epsg:3857", output_pyramid=True, output_native=False,
                 output_cell_type="uint16raw", output_histogram_buckets=512, output_tile_size=256,
                 output_resample_method="NearestNeighbor", output_key_index_method="ZCurveKeyIndexMethod",
                 ingest_resolution_meters=None):

        """
            Create a new ingest Layer

            Args:
                scene (rf.models.Scene): Scene instance the layer is based on
                output_uri (str): Output layer URI
                output_crs (str): Output layer CRS
                output_pyramid (bool): Whether or not to pyramid
                output_native (bool): Whether or not to save native resolution
                output_cell_type (bool): Output layer cell-type
                output_histogram_buckets (int): Output histogram bin count
                output_tile_size (int): Size of output tiles
                output_resample_method (str): GeoTrellis resample method
                output_key_index_method (str): GeoTrellis method for indexing keys
                ingest_resolution_meters (float): Optional resolution that will dictate which images
                    from the scene are used
        """

        self.scene = scene
        self.output_uri = output_uri
        self.output_crs = output_crs
        self.output_pyramid = output_pyramid
        self.output_native = output_native
        self.output_cell_type = output_cell_type
        self.output_histogram_buckets = output_histogram_buckets
        self.output_tile_size = output_tile_size
        self.output_resample_method = output_resample_method
        self.output_key_index_method = output_key_index_method
        self.ingest_resolution_meters = ingest_resolution_meters

    @classmethod
    def from_dict(cls, d):
        return cls(
            d.get('scene'),
            d.get('output_uri'),
            d.get('output_crs'),
            d.get('output_pyramid'),
            d.get('output_native'),
            d.get('output_cell_type'),
            d.get('output_histogram_buckets'),
            d.get('output_tile_size'),
            d.get('output_resample_method'),
            d.get('output_key_index_method'),
            d.get('ingest_resolution_meters')
        )

    def to_dict(self):
        return {
            'scene': self.scene.to_dict(),
            'output_uri': self.output_uri,
            'output_crs': self.output_crs,
            'output_pyramid': self.output_pyramid,
            'output_native': self.output_native,
            'output_cell_type': self.output_cell_type,
            'output_histogram_buckets': self.output_histogram_buckets,
            'output_tile_size': self.output_tile_size,
            'output_resample_method': self.output_resample_method,
            'output_key_index_method': self.output_key_index_method,
            'ingest_resolution_meters': self.ingest_resolution_meters
        }

    def to_ingest_dict(self):
        """ Return a dict formatted specifically for serialization to an ingest definition component """
        return {
            'id': str(self.scene.id),
            'output': self.get_output(),
            'sources': [s.to_dict() for s in self.get_sources()]
        }

    def get_sources(self):
        """ Return a list of sources created from the images within the layer's scene """
        sources = []
        target_band_index = 1
        for image in self.scene.images:
            new_source = Source(image, self.get_extent(), target_band_index)
            sources.append(new_source)
            target_band_index += len(new_source.band_maps)
        return sources

    def get_output(self):
        """ Return a dict with the layer's output parameters as needed for an ingest definition """
        return {
            'crs': self.output_crs,
            'pyramid': self.output_pyramid,
            'histogramBuckets': self.output_histogram_buckets,
            'uri': self.output_uri,
            'tileSize': self.get_tile_size(),
            'cellType': self.output_cell_type,
            'resampleMethod': self.output_resample_method,
            'keyIndexMethod': self.output_key_index_method,
            'native': self.output_native
        }

    def get_extent(self):
        """ Return a list of floats that represents the extent of the layer's data (not tile) """
        coords = self.scene.dataFootprint['coordinates'][0][0]
        x_coords = map(lambda c: c[0], coords)
        y_coords = map(lambda c: c[1], coords)
        return [
            min(x_coords),
            min(y_coords),
            max(x_coords),
            max(y_coords)
        ]

    def get_tile_size(self):
        """ Return a dict with width and height keys to represent a square tile size """
        return {
            'width': self.output_tile_size,
            'height': self.output_tile_size
        }
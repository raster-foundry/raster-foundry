""" Python class to represent the sources within an ingest layer """


class Source(object):
    def __init__(self, uri, extent, band_maps, cell_size,
                  extent_crs, source_crs=None):

        """
            Create a new ingest Source

            Args:
                uri (str): location of image
                extent (List[float]): extent of the source
                starting_target_band (int): index of first target band
                band_maps (List[dict]): list of mappings for each band within the image
                extent_crs (str): CRS of the extent
                crs (str): Optional CRS of the Source
        """
        self.uri = uri
        self.cell_size = cell_size
        self.extent = extent
        self.band_maps = band_maps
        self.source_crs = source_crs
        self.extent_crs = extent_crs

    @classmethod
    def from_dict(cls, d):
        return cls(
            d.get('image'),
            d.get('extent'),
            d.get('starting_target_band'),
            d.get('band_maps'),
            d.get('source_crs'),
            d.get('extent_crs')
        )

    def to_dict(self):
        d = {
            'crs': self.source_crs,
            'extentCrs': self.extent_crs,
            'cellSize': self.cell_size,
            'uri': self.uri,
            'extent': self.extent,
            'bandMaps': self.band_maps
        }

        if self.source_crs:
            d['crs'] = self.source_crs

        return d
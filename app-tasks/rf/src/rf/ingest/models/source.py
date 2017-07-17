""" Python class to represent the sources within an ingest layer """


class Source(object):
    def __init__(self, uri, band_maps, cell_size):

        """
            Create a new ingest Source

            Args:
                uri (str): location of image
                band_maps (List[dict]): list of mappings for each band within the image
        """
        self.uri = uri
        self.cell_size = cell_size
        self.band_maps = band_maps

    @classmethod
    def from_dict(cls, d):
        return cls(
            d.get('image'),
            d.get('band_maps'),
            d.get('cell_sieze')
        )

    def to_dict(self):
        d = {
            'cellSize': self.cell_size,
            'uri': self.uri,
            'bandMaps': self.band_maps
        }

        return d
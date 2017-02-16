""" Python class to represent the sources within an ingest layer """


class Source(object):
    def __init__(self, image, extent, starting_target_band, band_maps=None,
                 source_crs="epsg:4326", extent_crs="epsg:4326"):

        """
            Create a new ingest Source

            Args:
                image (rf.models.Image): Image instance the source is based on
                extent (List[float]): extent of the source
                starting_target_band (int): index of first target band
                band_maps (List[dict]): list of mappings for each band within the image
                crs (str): CRS of the Source (@TODO: figure out why this is needed)
                extent_crs (str): CRS of the extent (@TODO: figure out why this is needed)
        """

        self.image = image
        self.extent = extent
        self.starting_target_band = starting_target_band
        self.band_maps = band_maps or self.create_band_maps()
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
        return {
            'image': self.image.to_dict(),
            'extent': self.extent,
            'starting_target_band': self.starting_target_band,
            'band_maps': self.band_maps,
            'source_crs': self.source_crs,
            'extent_crs': self.extent_crs
        }

    def to_ingest_dict(self):
        """
            Return a dict of the source formatted to be serialized and included within an ingest
            definition
        """
        return {
            'extent': self.extent,
            'crsExtent': self.extent_crs,
            'bandMaps': self.band_maps,
            'crs': self.source_crs,
            'uri': self.image['sourceUri'],
        }

    def create_band_maps(self):
        """
            Return a list of dicts, each representing the mapping of a band within the source's
            image to a band of the target output
        """
        band_maps = []
        source_band_index = 1
        target_band_index = self.starting_target_band
        for band in self.image['bands']:
            band_maps.append({
                'source': source_band_index,
                'target': target_band_index
            })
            source_band_index += 1
            target_band_index += 1
        return band_maps
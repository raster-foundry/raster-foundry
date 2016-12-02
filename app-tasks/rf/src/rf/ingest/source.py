""" Python class to represent the sources within an ingest layer """
class Source():
    def __init__(self, image, extent, starting_target_band, band_maps=None,
                 crs="epsg:3857", extent_crs="epsg:3857"):

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
        self.band_maps = self.get_band_maps()
        self.crs = crs
        self.extent_crs = extent_crs


    @classmethod
    def from_dict(cls, d):
        return cls(
            d.get('image'),
            d.get('extent'),
            d.get('starting_target_band')
        )

    def to_dict(self):
        return {
            'extent': self.extent,
            'crsExtent': self.extent_crs,
            'crs': self.crs,
            'uri': self.image['sourceUri'],
            'bandMaps': self.get_band_maps()
        }

    def get_band_maps(self):
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
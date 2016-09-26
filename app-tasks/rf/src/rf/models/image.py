"""Python class representation of a Raster Foundry Image"""

from .base import BaseModel

class Image(BaseModel):

    URL_PATH = '/api/images/'

    def __init__(self, organizationId, rawDataBytes, visibility, filename,
                 sourceuri, bands, imageMetadata, scene=None):
        """Create a new Image

        Args:
            orgnizationId (str): UUID of organization that this scene belongs to
            rawDataBytes (int): size of image
            visibility (str): accessibility level for object
            filename (str): filename for image (displayed to users)
            sourceri (str): source of image
            bands (List[str]): list of bands in image
            imageMetadata (dict): extra information about the image
        """
        self.organizationId = organizationId
        self.rawDataBytes = rawDataBytes
        self.visibility = visibility
        self.filename = filename
        self.sourceuri = sourceuri
        self.bands = bands
        self.imageMetadata = imageMetadata
        self.scene = scene

    def __repr__(self):
        return '<Image: {}>'.format(self.filename)

    @classmethod
    def from_dict(cls, d):
        return cls(
            d.get('organizationId'), d.get('rawDataBytes'), d.get('visibility'), d.get('filename'),
            d.get('sourceuri'), d.get('bands'), d.get('imageMetadata'), d.get('scene')
        )

    def to_dict(self):
        image_dict = dict(
            organizationId=self.organizationId,
            rawDataBytes=self.rawDataBytes,
            visibility=self.visibility,
            filename=self.filename,
            sourceuri=self.sourceuri,
            bands=self.bands,
            imageMetadata=self.imageMetadata
        )

        if self.scene:
            image_dict['scene'] = self.scene
        return image_dict

    def create(self):
        assert self.scene, 'Scene is required to create an Image'
        return super(Image, self).create()
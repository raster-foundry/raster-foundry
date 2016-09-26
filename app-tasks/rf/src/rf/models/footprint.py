"""Python class representation of a Raster Foundry footprint"""

from .base import BaseModel


class Footprint(BaseModel):

    URL_PATH = '/api/footprints/'

    def __init__(self, organizationId, multipolygon, id=None, sceneId=None, createdAt=None, modifiedAt=None):
        """Create a new Footprint

        Args:
            orgnizationId (str): UUID of organization that this scene belongs to
            multipolygon (dict): geojson for footprint
            id (str): UUID for footprint
            createdAt (str): when object was created
            modifiedAt (str): when object was last modified
        """
        self.organizationId = organizationId
        self.multipolygon = multipolygon

        # Optional - Can be none
        self.id = id
        self.sceneId = sceneId
        self.createdAt = createdAt
        self.modifiedAt = modifiedAt

    def __repr__(self):
        return '<Footprint: {}>'.format(self.id)

    @classmethod
    def from_dict(cls, d):
        return cls(
            d.get('organizationId'), d.get('multipolygon'), d.get('id'), d.get('sceneId'),
            d.get('createdAt'), d.get('modifiedAt')
        )

    def to_dict(self):
        footprint_dict = dict(
            organizationId=self.organizationId,
            multipolygon=self.multipolygon
        )
        if self.id:
            footprint_dict['id'] = self.id
        if self.sceneId:
            footprint_dict['sceneId'] = self.sceneId
        if self.createdAt:
            footprint_dict['createdAt'] = self.createdAt
        if self.modifiedAt:
            footprint_dict['modifiedAt'] = self.modifiedAt
        return footprint_dict

    def create(self):
        assert self.sceneId, 'Scene ID is required to create a Footprint'
        return super(Footprint, self).create()
"""Python class representation of a Raster Foundry thumbnail"""

from .base import BaseModel


class Thumbnail(BaseModel):

    URL_PATH = '/api/thumbnails/'

    def __init__(self, widthPx, heightPx, thumbnailSize, url, id=None, sceneId=None):
        """Creates a new Thumbnail

        Args:
            widthPx (int): width of thumbnail
            heightPx (int): height of thumbnail
            thumbnailSize (str): size of image (small, large, square)
            url (str): location of thumbnail
            id (str): UUID of thumbnail
            scene (str): UUID of scene associated with thumbnail
        """
        self.widthPx = widthPx
        self.heightPx = heightPx
        self.thumbnailSize = thumbnailSize
        self.url = url

        self.id = id
        self.sceneId = sceneId

    def __repr__(self):
        return '<Thumbnail: size-{} loc-{}>'.format(self.thumbnailSize, self.url)

    @classmethod
    def from_dict(cls, d):
        return cls(
            d.get('widthPx'), d.get('heightPx'), d.get('thumbnailSize'), d.get('url'),
            d.get('id'), d.get('sceneId')
        )

    def to_dict(self):
        thumbnail_dict = dict(
            widthPx=self.widthPx,
            heightPx=self.heightPx,
            thumbnailSize=self.thumbnailSize,
            url=self.url
        )

        if self.id:
            thumbnail_dict['id'] = self.id
        if self.sceneId:
            thumbnail_dict['sceneId'] = self.sceneId
        return thumbnail_dict

    def create(self):
        assert self.sceneId, 'Scene ID is required to create a Thumbnail'
        return super(Thumbnail, self).create()

"""Python class representation of a Raster Foundry Ingest"""

import uuid


class Ingest(object):
    def __init__(self, id, layers):

        """
            Create a new Ingest

            Args:
                layers (List[Layer]): A list of all layers included in the ingest
        """

        assert len(layers), "An ingest requires at least one Layer"
        self.id = id or str(uuid.uuid4())
        self.layers = layers

    @classmethod
    def from_dict(cls, d):
        return cls(
            d.get('id'),
            d.get('layers')
        )

    def to_dict(self):
        return {
            'id': self.id,
            'layers': [l.to_dict() for l in self.layers]
        }

    def to_ingest_dict(self):
        """ Return a dict formatted specifically for serialization to an ingest definition """
        return {
            'id': self.id,
            'layers': [l.to_ingest_dict() for l in self.layers]
        }
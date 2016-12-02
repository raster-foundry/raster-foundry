import uuid

class Ingest():

    def __init__(self, layers=[]):
        """
            Create a new Ingest

            Args:
                layers (List[Layers]): A list of all layers included in the ingest
        """
        self.id = uuid.uuid4()
        self.layers = layers

    def to_dict(self):
        return {
            'id': str(self.id),
            'layers': map(lambda l: l.to_dict(), self.layers)
        }

    def create(self):
        return super(Ingest, self).create()
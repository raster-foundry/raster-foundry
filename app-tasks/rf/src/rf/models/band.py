"""Python class representation of Raster Foundry Image"""

from .base import BaseModel


class Band(BaseModel):

    def __init__(self, name, number, wavelength):
        """Create a new Band. Created band is a python representation of the Band.Create
        case class in the scala datamodel

        Args:
            name (str): Human-readable description of this band (e.g., "near-infrared")
            number (int): Number of this band in the scene this band's image belongs to
            wavelength (List[Int]): minimum and maximum wavelength of this band in nanometers
        """

        self.name = name
        self.number = number
        self.wavelength = wavelength

    def __repr__(self):
        return '<Band: {name}-{number}>'.format(name=self.name, number=self.number)

    @classmethod
    def from_dict(cls, d):
        return cls(
            d.get('name'), d.get('number'), d.get('wavelength')
        )

    def to_dict(self):
        return {'name': self.name, 'number': self.number, 'wavelength': self.wavelength}

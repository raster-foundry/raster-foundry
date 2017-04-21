"""A Project is a collection of zero or more scenes"""

class Project(object):
    """A Raster Foundry project"""

    TILE_PATH_TEMPLATE = '/tiles/{id}/{{z}}/{{x}}/{{y}}/'

    def __repr__(self):
        return '<Project - {}>'.format(self.name)

    def __init__(self, project, api):
        """Instantiate a new Project

        Args:
            project (Project): generated Project objects from specification
            api (API): api used to make additional requests on behalf of a project
        """
        self._project = project
        self.api = api

        # A few things we care about
        self.name = project.name
        self.id = project.id

    def tms(self):
        """Return a TMS URL for a project"""

        tile_path = self.TILE_PATH_TEMPLATE.format(id=self.id)
        return '{scheme}://{host}{tile_path}'.format(
            scheme=self.api.scheme, host=self.api.tile_host, tile_path=tile_path
        )
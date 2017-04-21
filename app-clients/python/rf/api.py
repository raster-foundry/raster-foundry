import os

from bravado.requests_client import RequestsClient
from bravado.client import SwaggerClient
from bravado.swagger_model import load_file
from simplejson import JSONDecodeError

from models import Project
from exceptions import RefreshTokenException


SPEC_PATH = os.path.join(os.path.dirname(os.path.realpath(__file__)), 'spec.yml')


class API(object):
    """Class to interact with Raster Foundry API"""

    def __init__(self, refresh_token=None, api_token=None,
                 host='app.rasterfoundry.com', scheme='https'):
        """Instantiate an API object to make requests to Raster Foundry's REST API

        Args:
            refresh_token (str): optional token used to obtain an API token to make API requests
            api_token (str): optional token used to authenticate API requests
            host (str): optional host to use to make API requests against
            scheme (str): optional scheme to override making requests with
        """

        self.http = RequestsClient()
        self.scheme = scheme

        spec = load_file(SPEC_PATH)

        spec['host'] = host
        spec['schemes'] = [scheme]

        split_host = host.split('.')
        split_host[0] = 'tiles'
        self.tile_host = '.'.join(split_host)

        config = {'validate_responses': False}
        self.client = SwaggerClient.from_spec(spec, http_client=self.http,
                                              config=config)

        if refresh_token and not api_token:
            api_token = self.get_api_token(refresh_token)
        elif not api_token:
            raise Exception('Must provide either a refresh token or API token')

        self.http.session.headers['Authorization'] = 'Bearer {}'.format(api_token)

    def get_api_token(self, refresh_token):
        """Retrieve API token given a refresh token

        Args:
            refresh_token (str): refresh token used to make a request for a new API token

        Returns:
            str
        """
        post_body = {'refresh_token': refresh_token}

        try:
            response = self.client.Authentication.post_tokens(refreshToken=post_body).future.result()
            return response.json()['id_token']
        except JSONDecodeError:
            raise RefreshTokenException('Error using refresh token, please verify it is valid')

    @property
    def projects(self):
        """List projects a user has access to

        Returns:
            List[Project]
        """
        has_next = True
        projects = []
        page = 0
        while has_next:
            paginated_projects = self.client.Imagery.get_projects(page=page).result()
            has_next = paginated_projects.hasNext
            page = paginated_projects.page + 1
            for project in paginated_projects.results:
                projects.append(Project(project, self))
        return projects

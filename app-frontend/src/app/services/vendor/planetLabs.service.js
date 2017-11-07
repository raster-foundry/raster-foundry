export default (app) => {
    class PlanetLabsService {
        constructor(
            $log, $http
        ) {
            'ngInject';
            this.$log = $log;
            this.$http = $http;
        }

        sendHttpRequest(req) {
            return this.$http(req).then(
                (response) => {
                    return response;
                },
                (error) => {
                    return error;
                }
            );
        }

        filterScenes(apiKey, requestBody) {
            // eslint-disable-next-line no-undef
            let token = btoa(apiKey + ':');
            let req = {
                'method': 'POST',
                'url': 'https://api.planet.com/data/v1/quick-search',
                'headers': {
                    'Content-Type': 'application/json',
                    'Authorization': 'Basic ' + token
                },
                data: requestBody
            };

            return this.sendHttpRequest(req);
        }

        getFilteredScenesNextPage(apiKey, link) {
            // eslint-disable-next-line no-undef
            let token = btoa(apiKey + ':');
            let req = {
                'method': 'GET',
                'url': link,
                'headers': {
                    'Authorization': 'Basic ' + token
                }
            };

            return this.sendHttpRequest(req);
        }

        getThumbnail(apiKey, link) {
            // eslint-disable-next-line no-undef
            let token = btoa(apiKey + ':');
            let req = {
                'method': 'GET',
                'url': link,
                'headers': {
                    'Authorization': 'Basic ' + token,
                    'Content-Type': 'arraybuffer'
                }
            };

            return this.sendHttpRequest(req);
        }
    }

    app.service('planetLabsService', PlanetLabsService);
};

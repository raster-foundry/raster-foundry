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
            let req = {
                'method': 'POST',
                'url': 'https://api.planet.com/data/v1/quick-search',
                'headers': {
                    'Content-Type': 'application/json',
                    'Authorization': 'Basic ' + apiKey
                },
                data: requestBody
            };

            return this.sendHttpRequest(req);
        }

        getFilteredScenesNextPage(apiKey, link) {
            let req = {
                'method': 'GET',
                'url': link,
                'headers': {
                    'Authorization': 'Basic ' + apiKey
                }
            };

            return this.sendHttpRequest(req);
        }

        getThumbnail(apiKey, link) {
            let req = {
                'method': 'GET',
                'url': link,
                'headers': {
                    'Authorization': 'Basic ' + apiKey,
                    'Content-Type': 'arraybuffer'
                }
            };

            return this.sendHttpRequest(req);
        }
    }

    app.service('planetLabsService', PlanetLabsService);
};

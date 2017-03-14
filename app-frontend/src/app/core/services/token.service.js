export default (app) => {
    class TokenService {
        constructor($resource, $q, $log) {
            'ngInject';
            this.$q = $q;
            this.$log = $log;

            this.ApiToken = $resource(
                '/api/tokens/:id', {
                    id: '@id'
                }, {
                    query: {
                        method: 'GET',
                        cache: false,
                        isArray: true
                    },
                    delete: {
                        method: 'DELETE'
                    }
                }
            );

            this.MapToken = $resource(
                '/api/map-tokens/:id', {
                    id: '@id'
                }, {
                    create: {
                        method: 'POST'
                    },
                    query: {
                        method: 'GET',
                        cache: false,
                        isArray: false
                    },
                    delete: {
                        method: 'DELETE'
                    },
                    update: {
                        method: 'PUT'
                    }
                });
        }

        queryApiTokens(params = {}) {
            return this.ApiToken.query(params).$promise;
        }

        deleteApiToken(params) {
            return this.ApiToken.delete(params).$promise;
        }

        createMapToken(params) {
            return this.MapToken.create(params).$promise;
        }

        queryMapTokens(params = {}) {
            return this.MapToken.query(params).$promise;
        }

        deleteMapToken(params) {
            return this.MapToken.delete(params).$promise;
        }

        updateMapToken(params) {
            return this.MapToken.update(params).$promise;
        }
    }

    app.service('tokenService', TokenService);
};

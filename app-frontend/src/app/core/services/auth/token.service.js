/* globals BUILDCONFIG */

export default (app) => {
    class TokenService {
        constructor($resource, $q, $log) {
            'ngInject';
            this.$q = $q;
            this.$log = $log;

            this.ApiToken = $resource(
                `${BUILDCONFIG.API_HOST}/api/tokens/:id`, {
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
                `${BUILDCONFIG.API_HOST}/api/map-tokens/:id`, {
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

        getOrCreateProjectMapToken(project) {
            let deferred = this.$q.defer();
            this.queryMapTokens({project: project.id}).then((response) => {
                let token = response.results.find((el) => el.name === project.name);
                if (token) {
                    deferred.resolve(token);
                } else {
                    this.createMapToken({
                        name: project.name,
                        project: project.id,
                        organizationId: project.organizationId
                    }).then((res) => {
                        // TODO: Toast this
                        this.$log.debug('token created!', res);
                        deferred.resolve(res);
                    }, (err) => {
                        // TODO: Toast this
                        deferred.reject('error creating token', err);
                    });
                }
            });
            return deferred.promise;
        }
    }

    app.service('tokenService', TokenService);
};

/* globals BUILDCONFIG */

export default (app) => {
    class TokenService {
        constructor($resource, $q, $log, APP_CONFIG, authService) {
            'ngInject';
            this.$q = $q;
            this.$log = $log;
            this.APP_CONFIG = APP_CONFIG;
            this.authService = authService;

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

            this.analysisMapToken = $resource(
                `${BUILDCONFIG.API_HOST}/api/map-tokens/`, {}, {
                    create: {
                        method: 'POST'
                    },
                    get: {
                        method: 'GET'
                    }
                }
            );

            this.auth0Token = $resource(
                `https://${APP_CONFIG.auth0Domain}/oauth/token`,
                {}, {
                    create: {
                        method: 'POST'
                    }
                }
            );
        }

        createApiToken(code) {
            return this.auth0Token.create({
                'grant_type': 'authorization_code',
                'client_id': this.APP_CONFIG.clientId,
                'redirect_uri': `${this.authService.getBaseURL()}/user/me/settings/api-tokens`,
                code
            }).$promise;
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
            return this.$q((resolve, reject) => {
                this.queryMapTokens({project: project.id}).then((response) => {
                    let token = response.results.find((el) => el.name === project.name);
                    if (token) {
                        resolve(token);
                    } else {
                        this.createMapToken({
                            name: project.name,
                            project: project.id,
                            organizationId: project.organizationId
                        }).then((res) => {
                            // TODO: Toast this
                            this.$log.debug('token created!', res);
                            resolve(res);
                        }, (err) => {
                            // TODO: Toast this
                            reject('error creating token', err);
                        });
                    }
                });
            });
        }

        createAnalysisMapToken(params) {
            return this.analysisMapToken.create(params).$promise;
        }

        getAnalysisMapTokens() {
            return this.analysisMapToken.get().$promise;
        }

        findToken(tokens, params) {
            if (params.project) {
                return tokens.results.find(t => t.project === params.project);
            } else if (params.toolRun) {
                // TODO swithc this to t.analysis once the backend is updated
                return tokens.results.find(t => t.toolRun === params.toolRun);
            }
            return null;
        }

        getOrCreateAnalysisMapToken(params) {
            let deferred = this.$q.defer();
            this.getAnalysisMapTokens().then((res) => {
                let token = this.findToken(res, params);
                if (token) {
                    deferred.resolve(token);
                } else {
                    this.createAnalysisMapToken(params).then((response) => {
                        this.$log.debug('token created!', response);
                        deferred.resolve(response);
                    }, (error) => {
                        deferred.reject('error creating token', error);
                    });
                }
            });
            return deferred.promise;
        }
    }

    app.service('tokenService', TokenService);
};

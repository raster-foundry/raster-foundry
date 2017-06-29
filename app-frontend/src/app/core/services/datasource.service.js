/* globals BUILDCONFIG */

export default (app) => {
    class DatasourceService {
        constructor($resource, authService) {
            'ngInject';

            this.authService = authService;

            this.Datasource = $resource(
                `${BUILDCONFIG.API_HOST}/api/datasources/:id/`, {
                    id: '@properties.id'
                }, {
                    query: {
                        method: 'GET',
                        cache: false
                    },
                    get: {
                        method: 'GET',
                        cache: false
                    },
                    create: {
                        method: 'POST'
                    },
                    update: {
                        method: 'PUT',
                        url: '/api/datasources/:id',
                        params: {
                            id: '@id'
                        }
                    }
                }
            );
        }

        query(params = {}) {
            return this.Datasource.query(params).$promise;
        }

        get(id) {
            return this.Datasource.get({id}).$promise;
        }

        createDatasource(name, composites, params = {}) {
            return this.authService.getCurrentUser().then(
                (user) => {
                    return this.Datasource.create({
                        organizationId: user.organizationId,
                        name: name,
                        visibility: params.visibility || 'PRIVATE',
                        composites: composites,
                        extras: params.extras || {}
                    }).$promise;
                },
                (error) => {
                    return error;
                }
            );
        }

        updateDatasource(updatedParams = {}) {
            return this.Datasource.update(updatedParams).$promise;
        }
    }

    app.service('datasourceService', DatasourceService);
};

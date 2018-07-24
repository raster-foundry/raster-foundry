/* globals BUILDCONFIG, _ */

export default (app) => {
    class DatasourceService {
        constructor($resource, $q, $cacheFactory, authService) {
            'ngInject';

            this.$q = $q;
            this.authService = authService;
            this.$cacheFactory = $cacheFactory;

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
                        cache: true
                    },
                    create: {
                        method: 'POST'
                    },
                    update: {
                        method: 'PUT',
                        url: `${BUILDCONFIG.API_HOST}/api/datasources/:id`,
                        params: {
                            id: '@id'
                        },
                        transformRequest: (payload) => {
                            let transformed = Object.assign(
                                {}, payload, {
                                    bands: _.map(
                                        payload.bands,
                                        band => {
                                            if (_.isString(band.wavelength)) {
                                                let bookends = band.wavelength.trim().split(',');
                                                band.wavelength = _.map(
                                                    bookends, (x) => Number.parseInt(x, 10)
                                                );
                                            }
                                            return band;
                                        }
                                    )
                                }
                            );
                            return angular.toJson(transformed);
                        }
                    },
                    delete: {
                        method: 'DELETE'
                    },
                    getPermissions: {
                        method: 'GET',
                        cache: true,
                        url: `${BUILDCONFIG.API_HOST}/api/datasources/:id/permissions`,
                        params: {
                            id: '@id'
                        },
                        isArray: true
                    }
                }
            );
        }

        query(params = {}) {
            return this.Datasource.query(params).$promise;
        }

        get(id) {
            if (Array.isArray(id)) {
                return this.$q.all(
                    id.map(ds => this.get(ds))
                );
            }
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
                        extras: params.extras || {},
                        bands: params.bands || {}
                    }).$promise;
                },
                (error) => {
                    return error;
                }
            );
        }

        updateDatasource(updatedParams = {}) {
            this.$cacheFactory.get('$http').remove(
                `${BUILDCONFIG.API_HOST}/api/datasources/${updatedParams.id}`
            );
            return this.Datasource.update(updatedParams).$promise;
        }

        getUnifiedColorComposites(datasources) {
            const composites = datasources.map(d => d.composites);
            return composites.reduce((union, comp) => {
                return Object.keys(comp).reduce((ao, ck) => {
                    if (ck in union) {
                        ao[ck] = union[ck];
                    }
                    return ao;
                }, {});
            }, composites[0]);
        }

        getUnifiedBands(datasources) {
            const bands = datasources.map(d => d.bands);
            const unifiedBands =
                _.every(bands, b => _.isEqual(b, bands[0])) ?
                    bands[0] :
                    this.generateDefaultBands(Math.min(bands.map(b => b.length)));

            return Array.isArray(unifiedBands) ? unifiedBands : [];
        }

        generateDefaultBands(count) {
            if (count) {
                return Array(count).fill().map((_, i) => ({
                    'name': `Band ${i}`,
                    'number': `${i}`
                }));
            }
            return [];
        }

        getPermissions(id) {
            return this.Datasource.getPermissions({id}).$promise;
        }

        deleteDatasource(id) {
            return this.Datasource.delete({id}).$promise;
        }
    }

    app.service('datasourceService', DatasourceService);
};

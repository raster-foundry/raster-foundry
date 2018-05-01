/* globals BUILDCONFIG */

export default (app) => {
    class DatasourceLicenseService {
        constructor($resource, $q, $cacheFactory, authService) {
            'ngInject';

            this.$q = $q;
            this.authService = authService;
            this.$cacheFactory = $cacheFactory;

            this.DatasourceLicense = $resource(
                `${BUILDCONFIG.API_HOST}/api/licenses/:id/`, {
                    id: '@properties.id'
                }, {
                    query: {
                        method: 'GET',
                        cache: false
                    },
                    get: {
                        method: 'GET',
                        cache: true
                    }
                }
            );
        }

        getLicenses(params = {}) {
            return this.DatasourceLicense.query(params).$promise;
        }

        getLicense(id) {
            return this.DatasourceLicense.get({id}).$promise;
        }
    }

    app.service('datasourceLicenseService', DatasourceLicenseService);
};

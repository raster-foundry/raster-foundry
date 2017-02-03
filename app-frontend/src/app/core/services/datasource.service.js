export default (app) => {
    class DatasourceService {
        constructor($resource) {
            'ngInject';

            this.Datasource = $resource(
                '/api/datasources/:id/', {
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

        query(params = {}) {
            return this.Datasource.query(params).$promise;
        }

        get(id) {
            return this.Datasource.get({id}).$promise;
        }
    }

    app.service('datasourceService', DatasourceService);
};

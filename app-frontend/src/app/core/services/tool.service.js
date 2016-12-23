export default (app) => {
    class ToolService {
        constructor($resource, $http) {
            'ngInject';
            this.$http = $http;
            this.Tool = $resource(
                '/api/tools/:id/', {
                    id: '@properties.id'
                }, {
                    query: {
                        method: 'GET',
                        cache: false
                    },
                    get: {
                        method: 'GET',
                        cache: false
                    }
                }
            );
        }

        query(params = {}) {
            return this.Tool.query(params).$promise;
        }

        get(id) {
            return this.Tool.get({id}).$promise;
        }

        // @TODO: implement getting related tags and categories
    }

    app.service('toolService', ToolService);
};

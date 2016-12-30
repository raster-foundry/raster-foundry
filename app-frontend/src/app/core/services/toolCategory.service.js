export default (app) => {
    class ToolCategoryService {
        constructor($resource) {
            'ngInject';

            this.ToolCategory = $resource(
                '/api/tool-categories/:id/', {
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
            return this.ToolCategory.query(params).$promise;
        }
    }

    app.service('toolCategoryService', ToolCategoryService);
};

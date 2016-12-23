export default (app) => {
    class ToolTagService {
        constructor($resource) {
            'ngInject';

            this.ToolTag = $resource(
                '/api/tool-tags/:id/', {
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
            return this.ToolTag.query(params).$promise;
        }
    }

    app.service('toolTagService', ToolTagService);
};

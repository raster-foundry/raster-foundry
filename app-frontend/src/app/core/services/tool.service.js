export default (app) => {
    class ToolService {
        constructor($resource, $http, userService) {
            'ngInject';
            this.$http = $http;
            this.userService = userService;
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
            this.ToolRun = $resource(
                '/api/tool-runs/:id/', {
                    id: '@properties.id'
                }, {
                    create: {
                        method: 'POST'
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

        createToolRun(toolRun) {
            return this.userService.getCurrentUser().then(
                (user) => {
                    return this.ToolRun.create(Object.assign(toolRun, {
                        organizationId: user.organizationId
                    })).$promise;
                },
                () => {

                }
            );
        }

        // @TODO: implement getting related tags and categories
    }

    app.service('toolService', ToolService);
};

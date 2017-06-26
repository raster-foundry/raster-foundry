export default (app) => {
    class ToolService {
        constructor($resource, $http, authService) {
            'ngInject';
            this.$http = $http;
            this.authService = authService;
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
            return this.authService.getCurrentUser().then(
                (user) => {
                    return this.ToolRun.create(Object.assign(toolRun, {
                        organizationId: user.organizationId
                    })).$promise;
                },
                () => {

                }
            );
        }

        generateSourcesFromTool(tool) {
            let nodes = [tool.definition];
            let sources = [];
            let sourceIds = [];
            let currentNode = 0;
            let shouldContinue = true;
            while (shouldContinue) {
                let args = nodes[currentNode].args || false;
                if (args) {
                    nodes = nodes.concat(args);
                }
                currentNode += 1;
                shouldContinue = currentNode < nodes.length;
            }
            nodes.forEach(n => {
                if (!n.apply) {
                    if (sourceIds.indexOf(n.id) < 0) {
                        sourceIds.push(n.id);
                        sources.push(n);
                    }
                }
            });
            return sources;
        }

        generateToolRun(tool) {
            const sources = this.generateSourcesFromTool(tool);
            return sources.reduce((tr, s) => {
                tr.executionParameters.sources[s.id] = {
                    id: false,
                    band: null,
                    type: 'project'
                };
                return tr;
            }, {
                visibility: 'PUBLIC',
                tool: tool.id,
                executionParameters: {
                    sources: {}
                }
            });
        }

        // @TODO: implement getting related tags and categories
    }

    app.service('toolService', ToolService);
};

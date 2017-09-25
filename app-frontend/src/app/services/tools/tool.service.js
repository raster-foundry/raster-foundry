/* globals BUILDCONFIG */

export default (app) => {
    class ToolService {
        constructor($resource, $http, $q, authService, APP_CONFIG) {
            'ngInject';
            this.$http = $http;
            this.$q = $q;
            this.authService = authService;
            this.Tool = $resource(
                `${BUILDCONFIG.API_HOST}/api/tools/:id/`, {
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
                    }
                }
            );
            this.ToolRun = $resource(
                `${BUILDCONFIG.API_HOST}/api/tool-runs/:id/`, {
                    id: '@properties.id'
                }, {
                    create: {
                        method: 'POST'
                    },
                    get: {
                        method: 'GET',
                        cache: false
                    },
                    histogram: {
                        url: `${APP_CONFIG.tileServerLocation}/tools/:toolId/histogram/`,
                        method: 'GET',
                        cache: false
                    },
                    statistics: {
                        url: `${APP_CONFIG.tileServerLocation}/tools/:toolId/statistics/`,
                        method: 'GET',
                        cache: false
                    }
                }
            );
        }

        query(params = {}) {
            return this.Tool.query(params).$promise;
        }

        searchQuery() {
            let deferred = this.$q.defer();
            let pageSize = 1000;
            let firstPageParams = {
                pageSize: pageSize,
                page: 0,
                sort: 'createdAt,desc'
            };

            let firstRequest = this.query(firstPageParams);

            firstRequest.then((page) => {
                let self = this;
                let numTools = page.count;
                let requests = [firstRequest];
                if (page.count > pageSize) {
                    let requestMaker = function *(totalResults) {
                        let pageNum = 1;
                        while (pageNum * pageSize <= totalResults) {
                            let pageParams = {
                                pageSize: pageSize,
                                page: pageNum,
                                sort: 'createdAt,desc'
                            };
                            yield self.query(pageParams);
                            pageNum += 1;
                        }
                    };

                    requests = requests.concat(Array.from(requestMaker(numTools)));
                }

                this.$q.all(requests).then(
                    (allResponses) => {
                        deferred.resolve(
                            allResponses.reduce((res, resp) => res.concat(resp.results), [])
                        );
                    },
                    () => {
                        deferred.reject('Error loading tools.');
                    }
                );
            }, () => {
                deferred.reject('Error loading tools.');
            });

            return deferred.promise;
        }

        get(id) {
            return this.Tool.get({id}).$promise;
        }

        createTool(toolBuffer) {
            return this.authService.getCurrentUser().then(
                user => {
                    const toolDefaults = {
                        organizationId: user.organizationId,
                        requirements: '',
                        license: '',
                        compatibleDataSources: [],
                        stars: 5.0,
                        tags: [],
                        categories: [],
                        owner: user.id
                    };
                    return this.Tool.create(Object.assign(toolDefaults, toolBuffer)).$promise;
                }
            );
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

        getNodeHistogram(toolRun, nodeId) {
            return this.ToolRun.histogram({
                toolId: toolRun, node: nodeId, voidCache: true,
                token: this.authService.token()
            }).$promise;
        }

        getNodeStatistics(toolRun, nodeId) {
            return this.ToolRun.statistics({
                toolId: toolRun,
                node: nodeId,
                voidCache: true,
                token: this.authService.token()
            }).$promise;
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

        loadTool(toolId) {
            this.isLoadingTool = true;
            this.currentToolId = toolId;
            const request = this.get(toolId);
            request.then(t => {
                this.currentTool = t;
            }, () => {
                this.currentToolId = null;
            }).finally(() => {
                this.isLoadingTool = false;
            });
            return request;
        }

        // @TODO: implement getting related tags and categories
    }

    app.service('toolService', ToolService);
};

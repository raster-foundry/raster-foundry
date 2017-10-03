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
                    id: '@id'
                }, {
                    create: {
                        method: 'POST'
                    },
                    get: {
                        method: 'GET',
                        cache: false
                    },
                    update: {
                        method: 'PUT'
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

        getToolRun(id) {
            return this.ToolRun.get({id}).$promise;
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

        updateToolRun(toolrun) {
            return this.ToolRun.update(toolrun).$promise;
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

        _assignTargetInToolRun(toolRun, targetId, assignment) {
            // If toolRun isn't an operator, it must be an identity tool; just edit the source
            // and return it.
            if (!toolRun.executionParameters.apply) {
                Object.assign(toolRun.executionParameters, assignment);
                return;
            }
            // Otherwise, update toolRun in place
            // We're doing a depth-first graph traversal on the arguments here.
            // Start "before the beginning" of the arguments of the root operator.
            let parents = [{node: toolRun.executionParameters, argIdx: -1}];
            if (toolRun.executionParameters.id === targetId) {
                Object.assign(toolRun.executionParameters, assignment);
            }
            while (parents.length > 0) {
                // Always consider the end of the parents stack; this is the current parent.
                let currParent = parents[parents.length - 1];
                // Then move to the next argument of the current parent
                currParent.argIdx += 1;
                // Now we have two options
                // 1. We're past the end of current parent's arguments. Pop parents and continue
                if (currParent.argIdx >= currParent.node.args.length) {
                    parents.pop();
                } else {
                    // 2. This parent still has arguments we haven't touched yet, so consider next
                    let currChild = currParent.node.args[currParent.argIdx];
                    let args = currChild.args || false;
                    // This child node is itself a parent. Push onto parents
                    if (args) {
                        parents.push({node: currChild, argIdx: -1});
                    }
                    // Edit it if it matches target. Usually this is leaf nodes, but not always
                    // (e.g. render definitions).
                    if (currChild.id === targetId) {
                        Object.assign(currChild, assignment);
                    }
                }
            }
        }

        updateToolRunSource(toolRun, sourceId, projectId, band) {
            this._assignTargetInToolRun(toolRun, sourceId, {
                band: band,
                type: 'projectSrc',
                projId: projectId,
                cellType: null
            });
        }

        updateToolRunConstant(toolRun, nodeId, constant) {
            this._assignTargetInToolRun(toolRun, nodeId, constant);
        }

        updateToolRunMetadata(toolRun, nodeId, metadata) {
            this._assignTargetInToolRun(toolRun, nodeId, {
                metadata: metadata
            });
        }

        generateSourcesFromTool(tool) {
            // Accepts a tool or tool run since they are pretty similar objects now.
            let nodes = [];
            if (tool.definition) {
                nodes.push(tool.definition);
            } else {
                nodes.push(tool.executionParameters);
            }
            let sources = {};
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
                // Only use projects, because constant nodes also have no apply.
                if (!n.apply && (n.type === 'src' || n.type === 'projectSrc')) {
                    if (sourceIds.indexOf(n.id) < 0) {
                        sourceIds.push(n.id);
                        sources[n.id] = n;
                    }
                }
            });
            return sources;
        }

        generateToolRun(tool) {
            // A tool run is quite similar to a tool except that the definition lives under
            // 'executionParameters' instead.
            return {
                visibility: 'PUBLIC',
                tool: tool.id,
                executionParameters: angular.copy(tool.definition)
            };
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

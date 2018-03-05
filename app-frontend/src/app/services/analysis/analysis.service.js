/* globals BUILDCONFIG */
import _ from 'lodash';

export default (app) => {
    class AnalysisService {
        constructor($resource, $http, $q, authService, APP_CONFIG) {
            'ngInject';
            this.$http = $http;
            this.$q = $q;
            this.authService = authService;
            this.Template = $resource(
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
                    },
                    update: {
                        method: 'PUT',
                        url: `${BUILDCONFIG.API_HOST}/api/tools/:id`
                    },
                    delete: {
                        method: 'DELETE'
                    }
                }
            );
            this.Analysis = $resource(
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
                    query: {
                        method: 'GET',
                        cache: false
                    },
                    update: {
                        method: 'PUT'
                    },
                    histogram: {
                        url: `${APP_CONFIG.tileServerLocation}/tools/:analysisId/histogram/`,
                        method: 'GET',
                        cache: false
                    },
                    statistics: {
                        url: `${APP_CONFIG.tileServerLocation}/tools/:analysisId/statistics/`,
                        method: 'GET',
                        cache: false
                    },
                    delete: {
                        method: 'DELETE'
                    }
                }
            );
        }

        fetchTemplates(params = {}) {
            return this.Template.query(params).$promise;
        }

        fetchAnalyses(params = {}) {
            return this.Analysis.query(params).$promise;
        }

        searchQuery() {
            let deferred = this.$q.defer();
            let pageSize = 1000;
            let firstPageParams = {
                pageSize: pageSize,
                page: 0,
                sort: 'createdAt,desc'
            };

            let firstRequest = this.Template.query(firstPageParams).$promise;

            firstRequest.then((page) => {
                let self = this;
                let numAnalyses = page.count;
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

                    requests = requests.concat(Array.from(requestMaker(numAnalyses)));
                }

                this.$q.all(requests).then(
                    (allResponses) => {
                        deferred.resolve(
                            allResponses.reduce((res, resp) => res.concat(resp.results), [])
                        );
                    },
                    () => {
                        deferred.reject('Error loading templates.');
                    }
                );
            }, () => {
                deferred.reject('Error loading templates.');
            });

            return deferred.promise;
        }

        getTemplate(id) {
            return this.Template.get({id}).$promise;
        }

        updateTemplate(template) {
            let update = _.cloneDeep(template);
            update.organizationId = update.organization.id;
            delete update.organization;
            return this.Template.update({id: template.id}, update).$promise;
        }

        deleteTemplate(id) {
            return this.Template.delete({id}).$promise;
        }

        deleteAnalysis(id) {
            return this.Analysis.delete({id}).$promise;
        }

        getAnalysis(id) {
            return this.Analysis.get({id}).$promise;
        }

        createTemplate(template) {
            return this.authService.getCurrentUser().then(
                user => {
                    const templateDefaults = {
                        organizationId: user.organizationId,
                        requirements: '',
                        license: '',
                        compatibleDataSources: [],
                        stars: 5.0,
                        tags: [],
                        categories: [],
                        owner: user.id
                    };
                    return this.Template.create(Object.assign(templateDefaults, template)).$promise;
                }
            );
        }

        createAnalysis(analysis) {
            return this.authService.getCurrentUser().then(
                (user) => {
                    return this.Analysis.create(Object.assign(analysis, {
                        organizationId: user.organizationId
                    })).$promise;
                },
                () => {

                }
            );
        }

        updateAnalysis(analysis) {
            return this.Analysis.update(analysis).$promise;
        }

        getNodeHistogram(analysis, nodeId) {
            return this.Analysis.histogram({
                analysisId: analysis, node: nodeId, voidCache: true,
                token: this.authService.token()
            }).$promise;
        }

        getNodeStatistics(analysis, nodeId) {
            return this.Analysis.statistics({
                analysisId: analysis,
                node: nodeId,
                voidCache: true,
                token: this.authService.token()
            }).$promise;
        }

        _assignTargetInAnalysis(analysis, targetId, assignment) {
            // If analysis isn't an operator, it must be an identity analysis; just edit the source
            // and return it.
            if (!analysis.executionParameters.apply) {
                Object.assign(analysis.executionParameters, assignment);
                return;
            }
            // Otherwise, update analysis in place
            // We're doing a depth-first graph traversal on the arguments here.
            // Start "before the beginning" of the arguments of the root operator.
            let parents = [{node: analysis.executionParameters, argIdx: -1}];
            if (analysis.executionParameters.id === targetId) {
                Object.assign(analysis.executionParameters, assignment);
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

        updateAnalysisSource(analysis, sourceId, projectId, band) {
            this._assignTargetInAnalysis(analysis, sourceId, {
                band: band,
                type: 'projectSrc',
                projId: projectId,
                cellType: null
            });
        }

        updateAnalysisConstant(analysis, nodeId, constant) {
            this._assignTargetInAnalysis(analysis, nodeId, constant);
        }

        updateAnalysisMetadata(analysis, nodeId, metadata) {
            this._assignTargetInAnalysis(analysis, nodeId, {
                metadata: metadata
            });
        }

        getAnalysisMetadata(analysis, nodeId) {
            // If analysis isn't an operator, it must be an identity analysis; return the metadata
            if (!analysis.executionParameters.apply) {
                return Object.assign({}, analysis.executionParameters.metadata);
            }
            // We're doing a depth-first graph traversal on the arguments here.
            // Start "before the beginning" of the arguments of the root operator.
            let parents = [{node: analysis.executionParameters, argIdx: -1}];
            if (analysis.executionParameters.id === nodeId) {
                return Object.assign({}, analysis.executionParameters.metadata);
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
                    if (currChild.id === nodeId) {
                        return Object.assign({}, currChild.metadata);
                    }
                }
            }
            return {};
        }

        generateSourcesFromAST(ast) {
            // Accepts a ast or ast run since they are pretty similar objects now.
            let nodes = [];
            if (ast.definition) {
                nodes.push(ast.definition);
            } else {
                nodes.push(ast.executionParameters);
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

        generateAnalysis(template) {
            // A template run is quite similar to a template except that the definition lives under
            // 'executionParameters' instead.
            return {
                visibility: 'PUBLIC',
                template: template.id,
                executionParameters: angular.copy(template.definition)
            };
        }

        loadTemplate(templateId) {
            this.isLoadingTemplate = true;
            this.currentTemplateId = templateId;
            const request = this.get(templateId);
            request.then(t => {
                this.currentTemplate = t;
            }, () => {
                this.currentTemplateId = null;
            }).finally(() => {
                this.isLoadingTemplate = false;
            });
            return request;
        }

        // @TODO: implement getting related tags and categories
    }

    app.service('analysisService', AnalysisService);
};

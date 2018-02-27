/* globals BUILDCONFIG */
import _ from 'lodash';

export default (app) => {
    class WorkspaceService {
        constructor(
            $resource, $http, $q,
            authService
        ) {
            this.$http = $http;
            this.$q = $q;
            this.authService = authService;

            this.Workspace = $resource(
                `${BUILDCONFIG.API_HOST}/api/workspaces/:id/`, {
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
                        url: `${BUILDCONFIG.API_HOST}/api/workspaces/:id`
                    },
                    delete: {
                        method: 'DELETE'
                    },
                    addAnalysis: {
                        method: 'POST',
                        url: `${BUILDCONFIG.API_HOST}/api/workspaces/:id/analyses`
                    }
                }
            );
        }

        fetchWorkspaces(params = {}) {
            return this.Workspace.query(params).$promise;
        }

        createWorkspace({name, description}) {
            return this.authService.getCurrentUser().then(user => {
                return this.Workspace.create({
                    name,
                    description,
                    organizationId: user.organizationId,
                    owner: user.id,
                    tags: [],
                    categories: []
                }).$promise;
            });
        }

        addAnalysis(workspaceId, analysis) {
            return this.Workspace.addAnalysis({id: workspaceId}, analysis).$promise;
        }

        deleteWorkspace(id) {
            return this.Workspace.delete({id}).$promise;
        }

        workspaceFromTemplate(template) {
            let analysis = _.get(template, 'latestVersion.analysis');
            if (analysis) {
                analysis.readonly = false;
                analysis.name = template.name;
            }
            let workspace = Object.assign(
                {
                    name: template.name,
                    description: template.description,
                    analyses: analysis ? [analysis] : []
                }
            );
            return workspace;
        }
    }

    app.service('workspaceService', WorkspaceService);
};

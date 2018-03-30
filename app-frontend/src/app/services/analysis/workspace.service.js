/* globals BUILDCONFIG */

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
    }

    app.service('workspaceService', WorkspaceService);
};

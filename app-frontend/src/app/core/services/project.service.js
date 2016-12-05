export default (app) => {
    class ProjectService {
        constructor($resource, userService, $http) {
            'ngInject';
            this.userService = userService;
            this.$http = $http;

            this.Project = $resource(
                '/api/projects/:id/', {
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
                    delete: {
                        method: 'DELETE'
                    },
                    updateProject: {
                        method: 'PUT',
                        url: '/api/projects/:id',
                        params: {
                            id: '@id'
                        }
                    },
                    addScenes: {
                        method: 'POST',
                        url: '/api/projects/:projectId/scenes/',
                        params: {
                            projectId: '@projectId'
                        },
                        isArray: true
                    },
                    projectScenes: {
                        method: 'GET',
                        cache: false,
                        url: '/api/projects/:projectId/scenes',
                        params: {
                            projectId: '@projectId'
                        }
                    },
                    removeScenes: {
                        method: 'DELETE',
                        url: '/api/projects/:projectId/scenes/',
                        params: {
                            projectId: '@projectId'
                        }
                    }
                }
            );
        }

        query(params = {}) {
            return this.Project.query(params).$promise;
        }

        createProject(name) {
            return this.userService.getCurrentUser().then(
                (user) => {
                    let publicOrg = user.organizations.filter(
                        (org) => org.name === 'Public'
                    )[0];
                    return this.Project.create({
                        organizationId: publicOrg.id, name: name, description: '',
                        visibility: 'PRIVATE', tags: []
                    }).$promise;
                },
                (error) => {
                    return error;
                }
            );
        }

        addScenes(projectId, sceneIds) {
            return this.Project.addScenes(
                {projectId: projectId},
                sceneIds
            ).$promise;
        }

        getProjectScenes(params) {
            return this.Project.projectScenes(params).$promise;
        }

        getProjectSceneCount(projectId) {
            return this.Project.projectScenes({projectId: projectId, limit: 1}).$promise;
        }

        removeScenesFromProject(projectId, scenes) {
            return this.$http({
                method: 'DELETE',
                url: `/api/projects/${projectId}/scenes/`,
                data: scenes,
                headers: {'Content-Type': 'application/json;charset=utf-8'}
            });
        }

        deleteProject(projectId) {
            return this.Project.delete({id: projectId}).$promise;
        }

        updateProject(params) {
            return this.Project.updateProject(params).$promise;
        }
    }

    app.service('projectService', ProjectService);
};

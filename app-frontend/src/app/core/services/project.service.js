export default (app) => {
    class ProjectService {
        constructor($resource, userService, $http, $q) {
            'ngInject';
            this.userService = userService;
            this.$http = $http;
            this.$q = $q;

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

        getProjectCorners(projectId) {
            return this.getAllProjectScenes({projectId: projectId}).then((scenes) => {
                let corners = {
                    lowerLeftLon: null,
                    lowerLeftLat: null,
                    upperRightLon: null,
                    upperRightLat: null
                };
                scenes.forEach(scene => {
                    let metadata = scene.sceneMetadata;
                    if (metadata.lowerLeftCornerLatitude < corners.lowerLeftLat ||
                        corners.lowerLeftLat === null) {
                        corners.lowerLeftLat = metadata.lowerLeftCornerLatitude;
                    }
                    if (metadata.lowerLeftCornerLongitude < corners.lowerLeftLon ||
                        corners.lowerLeftLon === null) {
                        corners.lowerLeftLon = metadata.lowerLeftCornerLongitude;
                    }
                    if (metadata.upperRightCornerLatitude < corners.upperRightLat ||
                        corners.upperRightLat === null) {
                        corners.upperRightLat = metadata.upperRightCornerLatitude;
                    }
                    if (metadata.upperRightCornerLongitude < corners.upperRightLon ||
                        corners.upperRightLon === null) {
                        corners.upperRightLon = metadata.upperRightCornerLongitude;
                    }
                });
                return corners;
            });
        }

        /** Return all scenes in a single collection, making multiple requests if necessary
         *
         * @param {object} params to pass as query params
         * @return {Promise} promise that will resolve when all scenes are available
         */
        getAllProjectScenes(params) {
            let deferred = this.$q.defer();
            // Figure out how many scenes there are
            this.getProjectSceneCount(params).then((sceneCount) => {
                let self = this;
                // We're going to use this in a moment to create the requests for API pages
                let requestMaker = function *(totalResults, pageSize) {
                    let pageNum = 0;
                    while (pageNum * pageSize <= totalResults) {
                        let pageParams = Object.assign({}, params, {
                            pageSize: pageSize,
                            page: pageNum,
                            sort: 'createdAt,desc'
                        });
                        yield self.getProjectScenes(pageParams);
                        pageNum = pageNum + 1;
                    }
                };
                let numScenes = sceneCount.count;
                // The default API pagesize is 30 so we'll use that.
                let pageSize = 30;
                // Generate requests for all pages
                let requests = Array.from(requestMaker(numScenes, pageSize));
                // Unpack responses into a single scene list.
                // The structure to unpack is:
                // [{ results: [{},{},...] }, { results: [{},{},...]},...]
                this.$q.all(requests).then(
                    (allResponses) => {
                        deferred.resolve(
                            allResponses.reduce((res, resp) => res.concat(resp.results), [])
                        );
                    },
                    () => {
                        deferred.reject('Error loading scenes.');
                    }
                );
            }, () => {
                deferred.reject('Error loading scenes.');
            });
            return deferred.promise;
        }

        getProjectSceneCount(params) {
            let countParams = Object.assign({}, params, {pageSize: 1, page: 0});
            return this.Project.projectScenes(countParams).$promise;
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

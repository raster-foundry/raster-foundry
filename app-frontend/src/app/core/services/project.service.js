/* global L */

export default (app) => {
    class ProjectService {
        constructor($resource, $location, tokenService, userService, statusService,
                    $http, $q, APP_CONFIG) {
            'ngInject';

            this.tokenService = tokenService;
            this.userService = userService;
            this.statusService = statusService;
            this.$http = $http;
            this.$location = $location;
            this.$q = $q;

            this.currentProject = null;

            this.tileServer = `${APP_CONFIG.tileServerLocation}`;

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
                    },
                    export: {
                        method: 'POST',
                        url: '/api/exports/'
                    }
                }
            );
        }

        query(params = {}) {
            return this.Project.query(params).$promise;
        }

        get(id) {
            return this.Project.get({id}).$promise;
        }

        export(projectId, zoom) {
            return this.userService.getCurrentUser().then(
                (user) => {
                    return this.Project.export({
                        organizationId: user.organizationId,
                        projectId: projectId,
                        exportStatus: 'NOTEXPORTED',
                        exportType: 'S3',
                        visibility: 'PRIVATE',
                        exportOptions: {
                            resolution: zoom
                        }
                    }).$promise;
                },
                (error) => {
                    return error;
                }
            );
        }

        createProject(name) {
            return this.userService.getCurrentUser().then(
                (user) => {
                    return this.Project.create({
                        organizationId: user.organizationId, name: name, description: '',
                        visibility: 'PRIVATE', tileVisibility: 'PRIVATE', tags: []
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
            let pageSize = 30;
            let firstPageParams = Object.assign({}, params, {
                pageSize: pageSize,
                page: 0,
                sort: 'createdAt,desc'
            });
            let firstRequest = this.getProjectScenes(firstPageParams);

            firstRequest.then((page) => {
                let self = this;
                let numScenes = page.count;
                let requests = [firstRequest];
                if (page.count > pageSize) {
                    let requestMaker = function *(totalResults) {
                        let pageNum = 1;
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

                    requests = requests.concat(Array.from(requestMaker(numScenes)));
                    // Unpack responses into a single scene list.
                    // The structure to unpack is:
                    // [{ results: [{},{},...] }, { results: [{},{},...]},...]
                }

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

        getProjectStatus(projectId) {
            return this.getAllProjectScenes({ projectId }).then(scenes => {
                if (scenes) {
                    const counts = scenes.reduce((acc, scene) => {
                        acc[scene.statusFields.ingestStatus] = acc[scene.statusFields.ingestStatus] + 1 || 1;
                        return acc;
                    }, {});
                    if (counts.FAILED) {
                        return 'FAILED';
                    } else if (counts.NOTINGESTING || counts.TOBEINGESTED || counts.INGESTING) {
                        return 'PARTIAL';
                    } else if (counts.INGESTED) {
                        return 'CURRENT';
                    }
                }
                return 'NOSCENES';
            });
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

        getBaseURL() {
            let host = this.$location.host();
            let protocol = this.$location.protocol();
            let port = this.$location.port();
            let formattedPort = port !== 80 && port !== 443 ? ':' + port : '';
            return `${protocol}://${host}${formattedPort}`;
        }

        getProjectLayerURL(project, token) {
            let params = {
                tag: new Date().getTime()
            };

            if (token) {
                params.token = token;
            }

            let formattedParams = L.Util.getParamString(params);

            return `${this.tileServer}/${project.id}/{z}/{x}/{y}/${formattedParams}`;
        }

        getProjectShareURL(project) {
            let deferred = this.$q.defer();
            let shareUrl = `${this.getBaseURL()}/#/share/${project.id}`;
            if (project.tileVisibility === 'PRIVATE') {
                this.tokenService.getOrCreateProjectMapToken(project).then((token) => {
                    deferred.resolve(`${shareUrl}/?mapToken=${token.id}`);
                });
            } else {
                deferred.resolve(shareUrl);
            }
            return deferred.promise;
        }

        loadProject(id) {
            this.isLoadingProject = true;
            this.currentProjectId = id;
            const request = this.get(id);
            request.then(
                p => {
                    this.currentProject = p;
                },
                () => {
                    this.currentProjectId = null;
                }
            ).finally(() => {
                this.isLoadingProject = false;
            });
            return request;
        }
    }

    app.service('projectService', ProjectService);
};

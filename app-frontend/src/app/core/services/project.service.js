/* global L */
const availableProcessingOptions = [
    {
        label: 'Color Corrected',
        description:
            `Export with the color-corrections and false color composites configured
            for this project`,
        value: 'current',
        default: true
    }, {
        label: 'Raw',
        value: 'raw',
        exportOptions: {
            raw: true
        }
    }, {
        label: 'NDVI',
        value: 'ndvi',
        description:
            'Assess whether the target being observed contains live green vegetation or not',
        toolId: '7311e8ca-9af7-4fab-b63e-559d2e765388'
    }
];

export default (app) => {
    class ProjectService {
        constructor(
            $resource, $location, $http, $q, APP_CONFIG,
            tokenService, authService, statusService
        ) {
            'ngInject';

            this.tokenService = tokenService;
            this.authService = authService;
            this.statusService = statusService;
            this.$http = $http;
            this.$location = $location;
            this.$q = $q;
            this.availableProcessingOptions = availableProcessingOptions;

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
                            projectId: '@projectId',
                            pending: '@pending'
                        }
                    },
                    projectAois: {
                        method: 'GET',
                        cache: false,
                        url: '/api/projects/:projectId/areas-of-interest',
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
                    },
                    createAOI: {
                        method: 'POST',
                        url: '/api/projects/:projectId/areas-of-interest/',
                        params: {
                            projectId: '@projectId'
                        }
                    },
                    approveScene: {
                        method: 'POST',
                        url: '/api/projects/:projectId/scenes/:sceneId/accept',
                        params: {
                            projectId: '@projectId',
                            sceneId: '@sceneId'
                        }
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

        export(project, settings = {}, options = {}) {
            const defaultOptions = {
                resolution: 9,
                stitch: false,
                crop: false
            };

            const finalOptions = Object.assign(defaultOptions, options);

            const defaultSettings = {
                projectId: project.id,
                exportStatus: 'NOTEXPORTED',
                exportType: 'S3',
                visibility: 'PRIVATE',
                exportOptions: finalOptions
            };

            const finalSettings = Object.assign(defaultSettings, settings);

            const userRequest = this.authService.getCurrentUser();

            return userRequest.then(
                (user) => {
                    return this.Project.export(
                        Object.assign(finalSettings, {
                            organizationId: user.organizationId
                        })
                    ).$promise;
                },
                (error) => {
                    return error;
                }
            );
        }

        createProject(name, params = {}) {
            return this.authService.getCurrentUser().then(
                (user) => {
                    return this.Project.create({
                        organizationId: user.organizationId,
                        name: name,
                        description: params.description || '',
                        visibility: params.visibility || 'PRIVATE',
                        tileVisibility: params.tileVisibility || 'PRIVATE',
                        tags: params.tags || [],
                        isAOIProject: params.isAOIProject || false
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
                        const ingestStatus = scene.statusFields.ingestStatus;
                        acc[ingestStatus] = acc[ingestStatus] + 1 || 1;
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

        createAOI(project, params) {
            return this.$q((resolve, reject) => {
                this.authService.getCurrentUser().then(user => {
                    const paramsWithOrg =
                          Object.assign(params, { organizationId: user.organizationId });
                    this.Project.createAOI(
                        {projectId: project},
                        paramsWithOrg
                    ).$promise.then(() => resolve(), (err) => reject(err));
                });
            });
        }

        approveScene(projectId, sceneId) {
            return this.Project.approveScene({ projectId, sceneId }).$promise;
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

        getZoomLevel(bbox) {
            let diffLng = Math.abs(bbox[0] - bbox[2]);
            let diffLat = Math.abs(bbox[1] - bbox[3]);

            // Scale down if latitude is less than 55 to adjust for
            // web mercator distortion
            let lngMultiplier = bbox[0] < 55 ? 0.8 : 1;
            let maxDiff = diffLng > diffLat ? diffLng : diffLat;
            let diff = maxDiff * lngMultiplier;
            if (diff >= 0.5) {
                return 8;
            } else if (diff >= 0.01 && diff < 0.5) {
                return 11;
            } else if (diff >= 0.005 && diff < 0.01) {
                return 16;
            }
            return 18;
        }

        getProjectThumbnailURL(project, token) {
            if (project.extent) {
                let coords = project.extent.coordinates[0];
                // Lower left and upper right coordinates in extent
                let bbox = [... coords[0], ... coords[2]];
                let params = {
                    bbox: bbox,
                    zoom: this.getZoomLevel(bbox),
                    token: token
                };
                let formattedParams = L.Util.getParamString(params);
                let url = `${this.tileServer}/${project.id}/export/${formattedParams}`;
                return url;
            }
            return null;
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

        getProjectAois(projectId) {
            return this.Project.projectAois({projectId: projectId}).$promise;
        }
    }

    app.service('projectService', ProjectService);
};

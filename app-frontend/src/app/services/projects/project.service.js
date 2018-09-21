/* globals BUILDCONFIG _ L*/
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
    }, {
        label: 'NDWI',
        value: 'ndwi',
        description:
            'An index that is primarily used to distinguish water bodies.',
        toolId: '2d3a351f-54b4-42a9-9db4-d027b9aac03c'
    }, {
        label: 'NDMI',
        value: 'ndmi',
        description:
            'An index that assesses the variation of the moisture content of vegetation.',
        toolId: '44fad5c9-1e0d-4631-aaa0-a61182619cb1'
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
            this.availableProcessingOptionsThin = this.availableProcessingOptions.slice(0, 2);

            this.tileServer = `${APP_CONFIG.tileServerLocation}`;

            this.Project = $resource(
                `${BUILDCONFIG.API_HOST}/api/projects/:id/`, {
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
                        url: `${BUILDCONFIG.API_HOST}/api/projects/:id`,
                        params: {
                            id: '@id'
                        }
                    },
                    addScenes: {
                        method: 'POST',
                        url: `${BUILDCONFIG.API_HOST}/api/projects/:projectId/scenes/`,
                        params: {
                            projectId: '@projectId'
                        }
                    },
                    projectDatasources: {
                        method: 'GET',
                        cache: false,
                        url: `${BUILDCONFIG.API_HOST}/api/projects/:projectId/datasources`,
                        params: {
                            projectId: '@projectId'
                        },
                        isArray: true
                    },
                    projectScenes: {
                        method: 'GET',
                        cache: false,
                        url: `${BUILDCONFIG.API_HOST}/api/projects/:projectId/scenes`,
                        params: {
                            projectId: '@projectId'
                        }
                    },
                    projectAois: {
                        method: 'GET',
                        cache: false,
                        url: `${BUILDCONFIG.API_HOST}/api/projects/:projectId/areas-of-interest`,
                        params: {
                            projectId: '@projectId'
                        }
                    },
                    removeScenes: {
                        method: 'DELETE',
                        url: `${BUILDCONFIG.API_HOST}/api/projects/:projectId/scenes/`,
                        params: {
                            projectId: '@projectId'
                        }
                    },
                    export: {
                        method: 'POST',
                        url: `${BUILDCONFIG.API_HOST}/api/exports/`
                    },
                    listExports: {
                        method: 'GET',
                        url: `${BUILDCONFIG.API_HOST}/api/exports?project=:project`,
                        params: {
                            project: '@project'
                        }
                    },
                    createAOI: {
                        method: 'POST',
                        url: `${BUILDCONFIG.API_HOST}/api/projects/:projectId/areas-of-interest/`,
                        params: {
                            projectId: '@projectId'
                        }
                    },
                    approveScenes: {
                        method: 'POST',
                        url: `${BUILDCONFIG.API_HOST}` +
                            '/api/projects/:projectId/scenes/accept',
                        params: {
                            projectId: '@projectId'
                        }
                    },
                    sceneOrder: {
                        method: 'GET',
                        url: `${BUILDCONFIG.API_HOST}/api/projects/:projectId/order/`,
                        cache: false,
                        params: {
                            projectId: '@projectId'
                        }
                    },
                    updateSceneOrder: {
                        method: 'PUT',
                        url: `${BUILDCONFIG.API_HOST}/api/projects/:projectId/order/`,
                        params: {
                            projectId: '@projectId'
                        }
                    },
                    colorMode: {
                        method: 'POST',
                        url: `${BUILDCONFIG.API_HOST}/api/projects/:projectId/project-color-mode/`,
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

        searchQuery() {
            return this.$q((resolve, reject) => {
                let pageSize = 1000;
                let firstPageParams = {
                    pageSize: pageSize,
                    page: 0,
                    sort: 'createdAt,desc'
                };

                let firstRequest = this.query(firstPageParams);

                firstRequest.then((page) => {
                    let self = this;
                    let num = page.count;
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

                        requests = requests.concat(Array.from(requestMaker(num)));
                    }

                    this.$q.all(requests).then(
                        (allResponses) => {
                            resolve(
                                allResponses.reduce((res, resp) => res.concat(resp.results), [])
                            );
                        },
                        () => {
                            reject('Error loading projects.');
                        }
                    );
                }, () => {
                    reject('Error loading projects.');
                });
            });
        }

        fetchProject(id) {
            return this.Project.get({id}).$promise;
        }

        listExports(params = {}) {
            return this.Project.listExports(params).$promise;
        }

        export(project, settings = {}, options = {}) {
            const defaultOptions = {
                resolution: 9,
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

        getProjectCorners(projectId) {
            return this.getAllProjectScenes({projectId: projectId}).then(({scenes}) => {
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

        getProjectScenes(projectId, params = {}) {
            return this.Project.projectScenes(
                Object.assign({}, {
                    projectId,
                    pending: false,
                    page: 0,
                    pageSize: 30
                }, params)
            ).$promise;
        }

        getProjectDatasources(projectId) {
            return this.Project.projectDatasources({projectId}).$promise;
        }

        getProjectStatus(projectId) {
            return this.$q.all({
                allScenes: this.getProjectScenes(
                    projectId, {page: 0, pageSize: 0}
                ),
                failedScenes: this.getProjectScenes(
                    projectId, {page: 0, pageSize: 0, ingestStatus: 'FAILED'}
                ),
                successScenes: this.getProjectScenes(
                    projectId, {page: 0, pageSize: 0, ingestStatus: 'INGESTED'}
                )
            }).then(({allScenes, failedScenes, successScenes}) => {
                if (failedScenes.count > 0) {
                    return 'FAILED';
                } else if (successScenes.count < allScenes.count) {
                    return 'PARTIAL';
                } else if (successScenes.count === allScenes.count && allScenes.count > 0) {
                    return 'CURRENT';
                }
                return 'NOSCENES';
            });
        }

        getProjectSceneCount(params) {
            let countParams = Object.assign({}, params, {pageSize: 0, page: 0});
            return this.Project.projectScenes(countParams).$promise;
        }

        removeScenesFromProject(projectId, scenes) {
            return this.$http({
                method: 'DELETE',
                url: `${BUILDCONFIG.API_HOST}/api/projects/${projectId}/scenes/`,
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

        approveScenes(projectId, sceneIds) {
            return this.Project.approveScenes(
                {projectId},
                sceneIds
            ).$promise;
        }

        getProjectLayerURL(project, params) {
            let projectId = typeof project === 'object' ? project.id : project;
            let queryParams = params || {};
            queryParams.tag = new Date().getTime();
            let formattedParams = L.Util.getParamString(queryParams);

            return `${this.tileServer}/${projectId}/{z}/{x}/{y}/${formattedParams}`;
        }

        getProjectShareLayerURL(project, token) {
            let projectId = typeof project === 'object' ? project.id : project;

            let params = {
                tag: new Date().getTime()
            };

            if (token) {
                params.token = token;
            }

            let formattedParams = L.Util.getParamString(params);

            return `${this.tileServer}/${projectId}/{z}/{x}/{y}/${formattedParams}`;
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
                let bbox = [...coords[0], ...coords[2]];
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

        getBaseURL() {
            let host = this.$location.host();
            let protocol = this.$location.protocol();
            let port = this.$location.port();
            let formattedPort = port !== 80 && port !== 443 ? ':' + port : '';
            return `${protocol}://${host}${formattedPort}`;
        }

        getProjectShareURL(project) {
            return this.$q((resolve, reject) => {
                let shareUrl = `${this.getBaseURL()}/share/${project.id}`;
                if (project.tileVisibility === 'PRIVATE') {
                    this.tokenService.getOrCreateProjectMapToken(project).then((token) => {
                        resolve(`${shareUrl}/?mapToken=${token.id}`);
                    }, (error) => reject(error));
                } else {
                    resolve(shareUrl);
                }
            });
        }

        getProjectAois(projectId) {
            return this.Project.projectAois({projectId: projectId}).$promise;
        }

        getSceneOrder(projectId) {
            const pageSize = 30;
            const firstPageParams = {
                pageSize,
                page: 0
            };
            return this.Project.sceneOrder({projectId: projectId}, firstPageParams)
                .$promise.then((res) => {
                    const scenes = res.results;
                    const count = res.count;
                    let promises = Array(
                        Math.ceil((count || 1) / pageSize) - 1
                    ).fill().map((x, page) => {
                        return this.Project.sceneOrder({
                            projectId,
                            pageSize,
                            page: page + 1
                        }).$promise.then((pageResponse) => pageResponse.results);
                    });
                    return this.$q.all(promises).then((sceneChunks) => {
                        const allScenes = scenes.concat(_.flatten(sceneChunks));
                        return allScenes;
                    });
                });
        }

        updateSceneOrder(projectId, sceneIds) {
            return this.Project.updateSceneOrder(
                {projectId: projectId},
                sceneIds
            ).$promise;
        }

        getAnnotationShapefile(projectId) {
            return this.$http({
                method: 'GET',
                url: `${BUILDCONFIG.API_HOST}/api/projects/${projectId}/annotations/shapefile`
            });
        }

        setProjectColorMode(projectId, bands) {
            return this.Project.colorMode({projectId}, bands).$promise;
        }
    }

    app.service('projectService', ProjectService);
};

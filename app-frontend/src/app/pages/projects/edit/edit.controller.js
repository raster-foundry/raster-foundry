import {Map} from 'immutable';

export default class ProjectsEditController {
    constructor( // eslint-disable-line max-params
        $log, $q, $state, $scope, modalService, $timeout,
        authService, projectService, projectEditService,
        mapService, mapUtilsService, layerService,
        datasourceService, imageOverlayService, thumbnailService
    ) {
        'ngInject';
        this.$log = $log;
        this.$q = $q;
        this.$state = $state;
        this.$scope = $scope;
        this.modalService = modalService;
        this.authService = authService;
        this.projectService = projectService;
        this.projectEditService = projectEditService;
        this.mapUtilsService = mapUtilsService;
        this.layerService = layerService;
        this.datasourceService = datasourceService;
        this.imageOverlayService = imageOverlayService;
        this.thumbnailService = thumbnailService;
        this.getMap = () => mapService.getMap('edit');
    }

    $onInit() {
        this.mosaicLayer = new Map();
        this.sceneLayers = new Map();
        this.projectId = this.$state.params.projectid;
        this.layers = [];

        if (!this.project) {
            if (this.projectId) {
                this.loadingProject = true;
                this.projectUpdateListeners = [];
                this.projectEditService.setCurrentProject(this.projectId, true).then(
                    (project) => {
                        this.project = project;
                        this.fitProjectExtent();
                        this.loadingProject = false;
                        this.projectUpdateListeners.forEach((wait) => {
                            wait.resolve(project);
                        });
                        this.getSceneList();
                        if (this.project.isAOIProject) {
                            this.getPendingSceneList();
                        }
                    },
                    () => {
                        this.loadingProject = false;
                    }
                );
            } else {
                this.$state.go('projects.list');
            }
        } else if (this.project.isAOIProject) {
            this.getPendingSceneList();
        }
    }

    $postLink() {
        if (this.project) {
            this.$timeout(this.fitProjectExtent(), 100);
        }
    }

    waitForProject() {
        return this.$q((resolve, reject) => {
            this.projectUpdateListeners.push({resolve, reject});
        });
    }

    fitProjectExtent() {
        this.getMap().then(m => {
            this.mapUtilsService.fitMapToProject(m, this.project);
        });
    }

    getSceneList() {
        this.sceneRequestState = {loading: true};

        const sceneListQuery = this.projectService.getAllProjectScenes(
            {
                projectId: this.projectId,
                pending: false
            }
        );

        sceneListQuery.then(
            (allScenes) => {
                this.addUningestedScenesToMap(allScenes.filter(
                    (scene) => scene.statusFields.ingestStatus !== 'INGESTED'
                ));

                this.sceneList = allScenes;
                for (const scene of this.sceneList) {
                    let scenelayer = this.layerService.layerFromScene(scene, this.projectId);
                    this.sceneLayers = this.sceneLayers.set(scene.id, scenelayer);
                }
                this.layerFromProject();
                this.initColorComposites();
            },
            (error) => {
                this.sceneRequestState.errorMsg = error;
            }
        ).finally(() => {
            this.sceneRequestState.loading = false;
        });

        return sceneListQuery;
    }

    addUningestedScenesToMap(scenes) {
        this.getMap().then((mapWrapper) => {
            mapWrapper.deleteLayers('Uningested Scenes');
            scenes.filter((scene) => {
                return scene.tileFootprint && scene.thumbnails && scene.thumbnails.length;
            }).forEach((scene) => {
                let thumbUrl = this.thumbnailService.getBestFitUrl(scene.thumbnails, 1000);
                let boundsGeoJson = L.geoJSON();
                boundsGeoJson.addData(scene.tileFootprint);
                let imageBounds = boundsGeoJson.getBounds();

                this.datasourceService.get(scene.datasource).then(d => {
                    let overlay = this.imageOverlayService.createNewImageOverlay(
                        thumbUrl,
                        imageBounds, {
                            opacity: 1,
                            dataMask: scene.dataFootprint,
                            thumbnail: thumbUrl,
                            attribution: `©${d.name} `
                        }
                    );
                    mapWrapper.addLayer(
                        'Uningested Scenes',
                        overlay,
                        {
                            showLayer: false
                        }
                    );
                });
            });
        });
    }

    getPendingSceneList() {
        if (!this.pendingSceneRequest) {
            this.pendingSceneRequest = this.projectService.getAllProjectScenes({
                projectId: this.projectId,
                pending: true
            });
            this.pendingSceneRequest.then(pendingScenes => {
                this.pendingSceneList = pendingScenes;
            });
        }
        return this.pendingSceneRequest;
    }

    layerFromProject() {
        let url = this.projectService.getProjectLayerURL(
            this.project,
            {token: this.authService.token()}
        );
        let layer = L.tileLayer(url, {
            maxZoom: 30
        });

        this.getMap().then(m => {
            m.setLayer('Ingested Scenes', layer, true);
        });
        this.mosaicLayer = this.mosaicLayer.set(this.projectId, layer);
    }

    setHoveredScene(scene) {
        if (!scene) {
            return;
        }
        let styledGeojson = Object.assign({}, scene.dataFootprint, {
            properties: {
                options: {
                    weight: 2,
                    fillOpacity: 0
                }
            }
        });
        this.getMap().then((map) => {
            map.setGeojson('footprint', styledGeojson);
        });
    }

    removeHoveredScene() {
        this.getMap().then((map) => {
            map.deleteGeojson('footprint');
        });
    }

    initColorComposites() {
        this.fetchUnifiedComposites(true).then(
            composites => {
                this.unifiedComposites = composites;
            }
        );
    }

    unifyComposites(datasources) {
        let composites = datasources.map(d => d.composites);
        return composites.reduce((union, comp) => {
            return Object.keys(comp).reduce((ao, ck) => {
                if (ck in union) {
                    ao[ck] = union[ck];
                }
                return ao;
            }, {});
        }, composites[0]);
    }

    fetchDatasources(force = false) {
        if (!this.datasourceRequest || force) {
            this.datasourceRequest = this.$q.all(
                this.sceneList.map(s => this.datasourceService.get(s.datasource))
            );
        }
        return this.datasourceRequest;
    }

    fetchUnifiedComposites(force = false) {
        if (!this.unifiedCompositeRequest || force) {
            this.unifiedCompositeRequest = this.fetchDatasources(force).then(
                datasources => {
                    return this.unifyComposites(datasources);
                }
            );
        }
        return this.unifiedCompositeRequest;
    }

    publishModal() {
        return this.modalService.open({
            component: 'rfProjectPublishModal',
            resolve: {
                project: () => this.project,
                tileUrl: () => this.projectService.getProjectLayerURL(this.project),
                shareUrl: () => this.projectService.getProjectShareURL(this.project)
            }
        });
    }

    openExportModal() {
        return this.getMap().then(m => {
            return m.map.getZoom();
        }).then(zoom => {
            return this.modalService.open({
                component: 'rfProjectExportModal',
                resolve: {
                    project: () => this.project,
                    zoom: () => zoom
                }
            });
        });
    }

}

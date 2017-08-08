const Map = require('es6-map');

export default class ProjectsEditController {
    constructor( // eslint-disable-line max-params
        $log, $q, $state, $scope, $uibModal,
        authService, projectService, mapService,
        mapUtilsService, layerService, datasourceService,
        imageOverlayService, thumbnailService
    ) {
        'ngInject';
        this.$log = $log;
        this.$q = $q;
        this.$state = $state;
        this.$scope = $scope;
        this.$uibModal = $uibModal;
        this.authService = authService;
        this.projectService = projectService;
        this.mapUtilsService = mapUtilsService;
        this.layerService = layerService;
        this.datasourceService = datasourceService;
        this.imageOverlayService = imageOverlayService;
        this.thumbnailService = thumbnailService;
        this.getMap = () => mapService.getMap('edit');
    }

    $onInit() {
        this.$scope.$on('$destroy', () => {
            if (this.activeModal) {
                this.activeModal.dismiss();
            }
        });

        this.mosaicLayer = new Map();
        this.sceneLayers = new Map();
        this.projectId = this.$state.params.projectid;
        this.layers = [];

        if (!this.project) {
            if (this.projectId) {
                this.loadingProject = true;
                this.projectUpdateListeners = [];
                this.fetchProject().then(
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

    waitForProject() {
        return this.$q((resolve, reject) => {
            this.projectUpdateListeners.push({resolve: resolve, reject: reject});
        });
    }

    fitProjectExtent() {
        this.getMap().then(m => {
            this.mapUtilsService.fitMapToProject(m, this.project);
        });
    }

    fetchProject() {
        if (!this.projectRequest) {
            this.projectRequest = this.projectService.loadProject(this.projectId);
        }
        return this.projectRequest;
    }

    getSceneList() {
        this.sceneRequestState = {loading: true};
        this.sceneListQuery = this.projectService.getAllProjectScenes(
            {
                projectId: this.projectId,
                pending: false
            }
        );
        this.sceneListQuery.then(
            (allScenes) => {
                this.addUningestedScenesToMap(allScenes.filter(
                    (scene) => scene.statusFields.ingestStatus !== 'INGESTED'
                ));

                this.sceneList = allScenes;
                for (const scene of this.sceneList) {
                    let scenelayer = this.layerService.layerFromScene(scene, this.projectId);
                    this.sceneLayers.set(scene.id, scenelayer);
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
                        true
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
            this.authService.token()
        );
        let layer = L.tileLayer(url, {
            maxZoom: 30
        });

        this.getMap().then(m => {
            m.setLayer('Ingested Scenes', layer, true);
        });
        this.mosaicLayer.set(this.projectId, layer);
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
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfProjectPublishModal',
            resolve: {
                project: () => this.project,
                tileUrl: () => this.projectService.getProjectLayerURL(this.project),
                shareUrl: () => this.projectService.getProjectShareURL(this.project)
            }
        });
    }

    openExportModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.getMap().then(m => {
            return m.map.getZoom();
        }).then(zoom => {
            this.activeModal = this.$uibModal.open({
                component: 'rfProjectExportModal',
                resolve: {
                    project: () => this.project,
                    zoom: () => zoom
                }
            });
        });
    }

    openImportModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfSceneImportModal',
            resolve: {
                project: () => this.project
            }
        });

        this.activeModal.result.then(() => {

        });

        return this.activeModal;
    }
}

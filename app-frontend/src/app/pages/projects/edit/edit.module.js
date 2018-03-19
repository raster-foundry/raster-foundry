import angular from 'angular';
import {Map, Set} from 'immutable';
import ProjectActions from '_redux/actions/project-actions';
import AnnotationActions from '_redux/actions/annotation-actions';
import _ from 'lodash';

class ProjectsEditController {
    constructor( // eslint-disable-line max-params
        $log, $q, $state, $scope, modalService, $timeout, $ngRedux, $location,
        authService, projectService, projectEditService,
        mapService, mapUtilsService, layerService,
        datasourceService, imageOverlayService, thumbnailService
    ) {
        'ngInject';
        this.$log = $log;
        this.$q = $q;
        this.$state = $state;
        this.$scope = $scope;
        this.$location = $location;
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

        let unsubscribe = $ngRedux.connect(
            () => ({}),
            Object.assign({}, ProjectActions, AnnotationActions)
        )(this);
        $scope.$on('$destroy', unsubscribe);
    }

    $onInit() {
        this.getMap().then(map => this.setProjectMap(map));
        this.mosaicLayer = new Map();
        this.sceneLayers = new Map();
        this.projectId = this.$state.params.projectid;

        if (!this.project) {
            if (this.projectId) {
                this.loadingProject = true;
                this.projectUpdateListeners = [];
                this.projectEditService.setCurrentProject(this.projectId, true).then(
                    (project) => {
                        this.project = project;
                        if (!this.$location.search().bbox) {
                            this.fitProjectExtent();
                        }
                        this.loadingProject = false;
                        this.projectUpdateListeners.forEach((wait) => {
                            wait.resolve(project);
                        });
                        this.getAndReorderSceneList();
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
        this.resetAnnotations();
        if (this.projectId) {
            this.setProjectId(this.projectId);
        }
    }

    $postLink() {
        if (this.project) {
            if (!this.$location.search().bbox) {
                this.fitProjectExtent();
            }
        }
    }

    getAndReorderSceneList() {
        this.projectService.getSceneOrder(this.projectId).then(
            (res) => {
                this.orderedSceneId = res.results;
            },
            () => {
                this.$log.log('error getting ordered scene IDs');
            }
        ).finally(() => {
            this.getSceneList().then(() => {
                this.getDatasources();
            });
        });
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

        this.sceneListQuery = this.projectService.getAllProjectScenes(
            {
                projectId: this.projectId,
                pending: false
            }
        ).then(
            (allScenes) => {
                this.addUningestedScenesToMap(allScenes.filter(
                    (scene) => scene.statusFields.ingestStatus !== 'INGESTED'
                ));
                return this.projectService.getSceneOrder(this.projectId).then(
                    (res) => {
                        this.orderedSceneId = res.results;
                        this.sceneList = _.uniqBy(this.orderedSceneId.map((id) => {
                            // eslint-disable-next-line
                            return _.find(allScenes, {id});
                        }), 'id');

                        this.sceneLayers = this.sceneList.map(scene => ({
                            id: scene.id,
                            layer: this.layerService.layerFromScene(scene, this.projectId)
                        })).reduce((sceneLayers, {id, layer}) => {
                            return sceneLayers.set(id, layer);
                        }, new Map());

                        this.layerFromProject();
                        this.initColorComposites();
                    },
                    (err) => {
                        this.$log.error('Error while adding scenes to projects', err);
                    }
                );
            },
            (error) => {
                this.sceneRequestState.errorMsg = error;
            }
        ).finally(() => {
            this.sceneRequestState.loading = false;
        });

        return this.sceneListQuery;
    }

    getDatasources() {
        if (this.sceneList) {
            const datasourceIds = [
                ...new Set(
                    this.sceneList.map(s => s.datasource)
                )
            ];
            this.datasourceService.get(datasourceIds).then(datasources => {
                this.datasources = datasources;
                this.bands = this.datasourceService.getUnifiedBands(this.datasources);
            });
        }
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
            this.unifiedCompositeRequest = this.fetchDatasources(force).then(datasources => {
                return this.datasourceService.getUnifiedColorComposites(datasources);
            });
        }
        return this.unifiedCompositeRequest;
    }

    publishModal() {
        this.modalService.open({
            component: 'rfProjectPublishModal',
            resolve: {
                project: () => this.project,
                tileUrl: () => this.projectService.getProjectLayerURL(this.project),
                shareUrl: () => this.projectService.getProjectShareURL(this.project)
            }
        });
    }

    openExportModal() {
        this.getMap().then(m => {
            return m.map.getZoom();
        }).then(zoom => {
            this.modalService.open({
                component: 'rfProjectExportModal',
                resolve: {
                    project: () => this.project,
                    zoom: () => zoom
                }
            });
        });
    }

}

const ProjectsEditModule = angular.module('pages.projects.edit', []);

ProjectsEditModule.controller('ProjectsEditController', ProjectsEditController);

export default ProjectsEditModule;

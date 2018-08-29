/* global BUILDCONFIG */
import angular from 'angular';
import {Map} from 'immutable';
import ProjectActions from '_redux/actions/project-actions';
import AnnotationActions from '_redux/actions/annotation-actions';
import _ from 'lodash';

class ProjectsEditController {
    constructor( // eslint-disable-line max-params
        $log, $q, $state, $scope, modalService, $timeout, $ngRedux, $location,
        authService, projectService, projectEditService,
        mapService, mapUtilsService, layerService,
        datasourceService, imageOverlayService, thumbnailService, sceneService,
        platform
    ) {
        'ngInject';
        $scope.autoInject(this, arguments);
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
        this.initialCenter = BUILDCONFIG.MAP_CENTER || [0, 0];
        this.initialZoom = BUILDCONFIG.MAP_ZOOM || 2;

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
            this.setPermissionsTab();
        }
    }

    $postLink() {
        if (this.project) {
            if (!this.$location.search().bbox) {
                this.fitProjectExtent();
            }
        }
    }

    setPermissionsTab() {
        this.projectService.fetchProject(this.projectId).then(thisProject => {
            this.projectOwnerId = thisProject.owner.id || thisProject.owner;
            this.actingUserId = this.authService.user.id;
        });
    }

    getAndReorderSceneList() {
        this.projectService.getSceneOrder(this.projectId).then(
            (res) => {
                this.orderedSceneIds = res.results;
            },
            () => {
                this.$log.log('error getting ordered scene IDs');
            }
        ).finally(() => {
            this.getSceneList();
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
            ({count: sceneCount, scenes: allScenes}) => {
                this.sceneCount = sceneCount;
                if (!this.sceneCount) {
                    this.orderedSceneIds = [];
                    this.sceneList = [];
                    this.sceneLayers = new Map();
                    return this.$q.resolve();
                }
                this.addUningestedScenesToMap(allScenes.filter(
                    (scene) => scene.statusFields.ingestStatus !== 'INGESTED'
                ));
                return this.projectService.getSceneOrder(this.projectId).then(
                    (orderedIds) => {
                        this.orderedSceneIds = orderedIds;
                        this.sceneList = _(
                          this.orderedSceneIds.map((id) => _.find(allScenes, {id}))
                        ).uniqBy('id').compact().value();


                        this.sceneLayers = this.sceneList.map(scene => ({
                            id: scene.id,
                            layer: this.layerService.layerFromScene(scene, this.projectId)
                        })).reduce((sceneLayers, {id, layer}) => {
                            return sceneLayers.set(id, layer);
                        }, new Map());

                        this.layerFromProject();
                        this.initColorComposites();
                        return this.fetchDatasources();
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

                this.sceneService.datasource(scene).then(d => {
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
            this.pendingSceneRequest.then(({count, scenes: pendingScenes}) => {
                this.pendingSceneCount = count;
                this.pendingSceneList = _(pendingScenes).uniqBy('id').compact().value();
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
            map.setGeojson('footprint', styledGeojson, {rectify: true});
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
                this.sceneList.map(s => this.sceneService.datasource(s))
            ).then((datasources) => {
                this.bands = this.datasourceService.getUnifiedBands(datasources);
                return datasources;
            });
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

    openShareModal() {
        this.modalService.open({
            component: 'rfPermissionModal',
            size: 'med',
            resolve: {
                object: () => this.project,
                permissionsBase: () => 'projects',
                objectType: () => 'PROJECT',
                objectName: () => this.project.name,
                platform: () => this.platform
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

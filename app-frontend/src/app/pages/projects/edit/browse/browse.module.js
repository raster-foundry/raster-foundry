/* global BUILDCONFIG */

import angular from 'angular';
import { Map } from 'immutable';
import _ from 'lodash';

class ProjectsSceneBrowserController {
    constructor( // eslint-disable-line max-params
        $log, $state, $location, $scope, $timeout,
        modalService, mapService,
        projectService, sessionStorage, planetLabsService, authService, featureFlags,
        RasterFoundryRepository, PlanetRepository, CMRRepository
    ) {
        'ngInject';
        this.$parent = $scope.$parent.$ctrl;
        this.$log = $log;
        this.$state = $state;
        this.$location = $location;
        this.$scope = $scope;
        this.$timeout = $timeout;
        this.modalService = modalService;
        this.projectService = projectService;
        this.sessionStorage = sessionStorage;
        this.mapService = mapService;
        this.planetLabsService = planetLabsService;
        this.authService = authService;

        this.helperText = null;
        this.zoomHelpText = 'Zoom in to search for scenes';
        this.repositories = [
            {
                label: 'Raster Foundry',
                overrideLabel: BUILDCONFIG.APP_NAME,
                service: RasterFoundryRepository
            }
        ];

        if (featureFlags.isOnByDefault('external-source-browse-planet')) {
            this.repositories.push({
                label: 'Planet Labs',
                service: PlanetRepository
            });
        }

        if (featureFlags.isOnByDefault('external-source-browse-cmr')) {
            this.repositories.push({
                label: 'NASA CMR',
                service: CMRRepository
            });
        }

        this.getMap = () => this.mapService.getMap('edit');
        this.getPreviewMap = () => this.mapService.getMap('preview');
    }

    $onInit() {
        this.projectScenesReady = false;
        this.allSelected = false;
        this.registerClick = true;
        this.selectedScenes = new Map();
        this.sceneList = [];
        this.sceneCount = null;
        this.planetThumbnailUrls = new Map();

        this.setBboxParam = _.debounce((bbox) => {
            this.$location.search('bbox', bbox).replace();
        }, 500);
        this.debouncedSceneFetch = _.debounce(this.fetchNextScenes.bind(this), 500);

        if (!this.$parent.project) {
            this.project = this.$parent.project;
            this.$parent.waitForProject().then((project) => {
                this.project = project;
                this.initParams();
                this.getProjectSceneIds();
                this.initMap();
            });
        } else {
            this.project = this.$parent.project;
            this.initParams();
            this.getProjectSceneIds();
            this.initMap();
        }

        this.$scope.$on('$destroy', () => {
            this.getMap().then(browseMap => {
                browseMap.deleteLayers('filterBboxLayer');
                browseMap.deleteLayers('Selected Scenes');
                browseMap.deleteThumbnail();
            });
            this.selectNoScenes();
        });
    }

    initParams() {
        this.queryParams = this.$location.search();
    }

    /**
     * Convert a string in Leaflet bbox coordinate format ("swlng,swlat,nelng,nelat") to array
     * @param {string} bboxString The bbox coordinate string to parse
     * @return {array} lat/lon coordinates specifying bounding box corners ([[0,0], [1.0, 1.0]])
     */
    parseBBoxString(bboxString) {
        let bbox = [];
        if (bboxString && bboxString.length) {
            let coordsStrings = bboxString.split(',');
            let coords = _.map(coordsStrings, str => parseFloat(str));
            // Leaflet expects nested coordinate arrays
            bbox = [
                [coords[1], coords[0]],
                [coords[3], coords[2]]
            ];
        }
        return bbox;
    }

    initMap() {
        if (this.queryParams.bbox) {
            this.bounds = this.parseBBoxString(this.queryParams.bbox);
        } else if (this.project && this.project.extent) {
            this.bounds = L.geoJSON(this.project.extent).getBounds();
        } else {
            this.bounds = [[-30, -90], [50, 0]];
        }
        this.getMap().then(browseMap => {
            browseMap.map.fitBounds(this.bounds);
            browseMap.on('contextmenu', ($event) => {
                $event.originalEvent.preventDefault();
                return false;
            });
            browseMap.on('movestart', () => {
                this.registerClick = false;
                return false;
            });
            browseMap.on('moveend', ($event, mapWrapper) => {
                this.$timeout(() => {
                    this.registerClick = true;
                }, 125);
                this.onViewChange(
                    mapWrapper.map.getBounds(),
                    mapWrapper.map.getCenter(),
                    mapWrapper.map.getZoom()
                );
            });
            if (browseMap.zoom < 8) {
                this.helperText = this.zoomHelpText;
                this.sceneList = [];
            }
        });
    }

    getProjectSceneIds() {
        this.projectService.getAllProjectScenes({ projectId: this.project.id }).then((scenes) => {
            this.projectSceneIds = scenes.map(s => s.id);
            this.projectScenesReady = true;
        });
    }

    onRepositoryChange(bboxFetchFactory, repository) {
        this.bboxFetchFactory = bboxFetchFactory;
        this.currentRepository = repository;

        this.sceneList = [];
        this.getMap().then((mapWrapper) => {
            if (mapWrapper.map.getZoom() < 8 && repository.label === 'Raster Foundry') {
                this.helperText = this.zoomHelpText;
            } else if (this.bboxCoords || this.queryParams && this.queryParams.bbox) {
                this.helperText = null;
                this.fetchNextScenesForBbox = this.bboxFetchFactory(
                    this.bboxCoords || this.queryParams.bbox
                );
                this.fetchNextScenes();
            } else {
                this.helperText = null;
                this.onViewChange(
                    mapWrapper.map.getBounds(),
                    mapWrapper.map.getCenter(),
                    mapWrapper.map.getZoom()
                );
            }
        });
    }

    onViewChange(newBounds, newCenter, zoom) {
        // This gives 400 status code under planet data filter
        // when the map extent is at a too big area
        this.bboxCoords = newBounds.toBBoxString();
        this.$parent.zoom = zoom;
        this.$parent.center = newCenter;

        this.setBboxParam(this.bboxCoords);
        if (this.currentRepository.label === 'Raster Foundry' && zoom < 8) {
            this.sceneList = [];
            this.sceneCount = 0;
            this.helperText = this.zoomHelpText;
            return;
        }
        this.helperText = null;
        this.onBboxChange();
    }

    onBboxChange() {
        let queryParams = this.$location.search();
        if (this.bboxFetchFactory && !queryParams.shape) {
            this.fetchNextScenesForBbox = this.bboxFetchFactory(this.bboxCoords);
            this.sceneList = [];
            this.debouncedSceneFetch();
        }
    }

    fetchNextScenes() {
        this.fetchingScenes = true;
        this.fetchError = false;
        this.hasNext = false;
        this.sceneCount = null;
        let factoryFn = this.bboxFetchFactory;
        let fetchScenes = this.fetchNextScenesForBbox;
        this.fetchNextScenesForBbox().then(({scenes, hasNext, count}) => {
            if (fetchScenes === this.fetchNextScenesForBbox &&
                factoryFn === this.bboxFetchFactory) {
                this.fetchingScenes = false;
                this.sceneList = this.sceneList.concat(scenes);
                this.hasNext = hasNext;
                this.sceneCount = count;
            }
        }, (err) => {
            if (fetchScenes === this.fetchNextScenesForBbox &&
                factoryFn === this.bboxFetchFactory) {
                this.fetchingScenes = false;
                this.fetchError = err;
                this.hasNext = false;
            }
        });
    }

    toggleFilterPane() {
        this.showFilterPane = !this.showFilterPane;
    }

    setHoveredScene(scene) {
        if (scene !== this.hoveredScene) {
            this.hoveredScene = scene;
            this.getMap().then((map) => {
                if (scene.sceneType !== 'COG') {
                    map.setThumbnail(scene, this.currentRepository);
                } else {
                    map.setLayer(
                      'Hovered Scene',
                      L.tileLayer(
                        this.sceneService.getSceneLayerURL(
                            scene,
                            {token: this.authService.token()}
                        ),
                        {maxZoom: 30})
                    );
                }
            });
        }
    }

    removeHoveredScene() {
        this.getMap().then((map) => {
            if (this.hoveredScene.sceneType === 'COG') {
                map.deleteLayers('Hovered Scene');
            } else {
                map.deleteThumbnail();
            }
            delete this.hoveredScene;
        });
    }

    setSelected(scene, selected) {
        this.getMap().then((map) => {
            if (selected) {
                this.selectedScenes = this.selectedScenes.set(scene.id, {
                    scene,
                    repository: this.currentRepository
                });
                map.setThumbnail(scene, this.currentRepository, {persist: true});
            } else {
                this.selectedScenes = this.selectedScenes.delete(scene.id);
                map.deleteThumbnail(scene);
            }
        });
    }

    isSelected(scene) {
        return scene && scene.id && this.selectedScenes.has(scene.id);
    }

    selectAllScenes() {
        if (this.allSelected && this.sceneList.length) {
            this.sceneList.map((scene) => this.setSelected(scene, false));
            this.getMap().then((map) => {
                map.deleteThumbnail();
            });
        } else if (this.sceneList.length) {
            this.sceneList.map((scene) => this.setSelected(scene, true));
        }
        this.allSelected = !this.allSelected;
    }

    selectNoScenes() {
        this.selectedScenes.forEach(s => this.setSelected(s, false));
    }

    sceneModal() {
        this.modalService.open({
            component: 'rfProjectAddScenesModal',
            resolve: {
                scenes: () => this.selectedScenes,
                selectScene: () => this.setSelected.bind(this),
                selectNoScenes: () => this.selectNoScenes.bind(this),
                repository: () => this.currentRepository,
                project: () => this.project
            },
            backdrop: 'static'
        }).result.then((sceneIds) => {
            this.projectSceneIds = this.projectSceneIds.concat(sceneIds);
            this.selectNoScenes();
        }).finally(() => {
            this.$parent.getSceneList();
        });
    }

    isInProject(scene) {
        if (scene && scene.id && this.projectScenesReady) {
            const index = this.projectSceneIds.indexOf(scene.id);
            return index >= 0;
        }
        return false;
    }

    gotoProjectScenes() {
        this.selectNoScenes();
        this.$state.go('projects.edit.scenes');
    }
}

const ProjectsSceneBrowserModule = angular.module('pages.projects.edit.browse', []);

ProjectsSceneBrowserModule.controller(
    'ProjectsSceneBrowserController', ProjectsSceneBrowserController
);

export default ProjectsSceneBrowserModule;

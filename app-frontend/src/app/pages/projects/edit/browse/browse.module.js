/* global BUILDCONFIG */

import angular from 'angular';
import { Map } from 'immutable';
import _ from 'lodash';

class ProjectsSceneBrowserController {
    constructor( // eslint-disable-line max-params
        $log, $state, $location, $scope, $timeout,
        modalService, mapService, sceneService,
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
        this.sceneService = sceneService;

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
        this.registerClick = true;
        this.sceneList = [];
        this.sceneCount = null;
        this.planetThumbnailUrls = new Map();
        this.scenesBeingAdded = [];

        this.setBboxParam = _.debounce((bbox) => {
            this.$location.search('bbox', bbox).replace();
        }, 500);
        this.debouncedSceneFetch = _.debounce(this.fetchNextScenes.bind(this), 500);

        if (!this.$parent.project) {
            this.project = this.$parent.project;
            this.$parent.waitForProject().then((project) => {
                this.project = project;
                this.initParams();
                this.initMap();
            });
        } else {
            this.project = this.$parent.project;
            this.initParams();
            this.initMap();
        }

        this.$scope.$on('$destroy', () => {
            this.getMap().then(browseMap => {
                browseMap.deleteLayers('filterBboxLayer');
                browseMap.deleteThumbnail();
            });
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
        });
    }

    onRepositoryChange(bboxFetchFactory, repository) {
        this.bboxFetchFactory = bboxFetchFactory;
        this.currentRepository = repository;

        this.sceneList = [];
        this.getMap().then((mapWrapper) => {
            if (this.bboxCoords || this.queryParams && this.queryParams.bbox) {
                this.fetchNextScenesForBbox = this.bboxFetchFactory(
                    this.bboxCoords || this.queryParams.bbox
                );
                this.fetchNextScenes();
            } else {
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

    isInProject(scene) {
        return scene && scene.inProject;
    }

    gotoProjectScenes() {
        this.$state.go('projects.edit.scenes');
    }

    addSceneToProject(scene) {
        this.scenesBeingAdded = [...this.scenesBeingAdded, scene.id];
        return this.currentRepository
            .service
            .addToProject(this.project.id, [scene])
            .then(() => {
                scene.inProject = true;
            })
            .finally(() => {
                this.$parent.fetchPage();
                this.$parent.layerFromProject();
                this.scenesBeingAdded = this.scenesBeingAdded.filter(s => s !== scene.id);
            });
    }

    addVisibleScenesToProject() {
        const scenesToAdd = this.sceneList.filter(s => !this.isInProject(s));
        const sceneIdsToAdd = scenesToAdd.map(s => s.id);
        this.scenesBeingAdded = [...this.scenesBeingAdded, ...sceneIdsToAdd];
        return this.currentRepository
            .service
            .addToProject(this.project.id, scenesToAdd)
            .then(() => {
                scenesToAdd.forEach(scene => {
                    scene.inProject = true;
                });
            })
            .finally(() => {
                this.$parent.fetchPage();
                this.$parent.layerFromProject();
                this.scenesBeingAdded =
                    this.scenesBeingAdded.filter(s => sceneIdsToAdd.includes(s));
            });
    }

    hasDownloadPermission(scene) {
        if (this.currentRepository.service.getScenePermissions(scene).includes('download')) {
            return true;
        }
        return false;
    }
}

const ProjectsSceneBrowserModule = angular.module('pages.projects.edit.browse', []);

ProjectsSceneBrowserModule.controller(
    'ProjectsSceneBrowserController', ProjectsSceneBrowserController
);

export default ProjectsSceneBrowserModule;

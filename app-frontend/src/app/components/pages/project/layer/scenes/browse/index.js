/* global BUILDCONFIG */

import { Set, Map } from 'immutable';
import _ from 'lodash';

import tpl from './index.html';

const mapLayerName = 'Project Layer';

class LayerScenesBrowseController {
    constructor(
        $rootScope,
        $location,
        $state,
        $scope,
        $timeout,
        mapService,
        featureFlags,
        authService,
        planetLabsService,
        sessionStorage,
        modalService,
        sceneService,
        projectService,
        RasterFoundryRepository,
        PlanetRepository
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.selected = new Map();
        this.visibleScenes = new Map();
        this.scenesOnMap = new Set();
        this.sceneActions = new Map();
        this.repositories = [
            {
                label: 'Raster Foundry',
                overrideLabel: BUILDCONFIG.APP_NAME,
                service: this.RasterFoundryRepository
            }
        ];

        if (this.featureFlags.isOnByDefault('external-source-browse-planet')) {
            this.repositories.push({
                label: 'Planet Labs',
                service: this.PlanetRepository
            });
        }

        this.projectScenesReady = false;
        this.registerClick = true;
        this.sceneList = [];
        this.sceneCount = null;
        this.planetThumbnailUrls = new Map();
        this.scenesBeingAdded = new Set();

        this.setBboxParam = _.debounce(bbox => {
            this.$location.search('bbox', bbox).replace();
        }, 500);
        this.debouncedSceneFetch = _.debounce(this.fetchNextScenes.bind(this), 500);

        this.resetParams();
        this.initMap();
        this.setMapLayers();
    }

    $onDestroy() {
        this.removeMapLayers();
        this.hideAll();
        this.getMap().then(mapContainer => {
            mapContainer.deleteLayers('filterBboxLayer');
            mapContainer.deleteThumbnail();
            this.scenesOnMap.forEach(id => {
                mapContainer.deleteGeojson(id);
            });
            this.mapListeners.forEach(listener => {
                mapContainer.off(listener);
            });
        });
    }

    hideAll() {
        this.getMap().then(mapContainer => {
            this.visibleScenes = new Map();
            this.syncVisibleScenes();
        });
    }

    resetParams() {
        this.queryParams = this.$location.search();
    }

    getMap() {
        return this.mapService.getMap('project');
    }

    setMapLayers() {
        let mapLayer = this.projectService.mapLayerFromLayer(this.project, this.layer);
        return this.getMap().then(map => {
            map.setLayer(mapLayerName, mapLayer, true);
        });
    }

    removeMapLayers() {
        return this.getMap().then(map => {
            map.deleteLayers(mapLayerName);
        });
    }

    initMap() {
        this.resetParams();
        if (this.queryParams.bbox) {
            this.bounds = this.parseBBoxString(this.queryParams.bbox);
        } else if (this.project && this.project.extent) {
            this.bounds = L.geoJSON(this.project.extent).getBounds();
        } else {
            this.bounds = [
                [-30, -90],
                [50, 0]
            ];
        }
        this.getMap().then(mapContainer => {
            mapContainer.map.fitBounds(this.bounds);
            this.mapListeners = [
                mapContainer.on('contextmenu', $event => {
                    $event.originalEvent.preventDefault();
                    return false;
                }),
                mapContainer.on('movestart', () => {
                    this.registerClick = false;
                    return false;
                }),
                mapContainer.on('moveend', ($event, mapWrapper) => {
                    this.$timeout(() => {
                        this.registerClick = true;
                    }, 125);
                    this.onViewChange(
                        mapWrapper.map.getBounds(),
                        mapWrapper.map.getCenter(),
                        mapWrapper.map.getZoom()
                    );
                })
            ];
        });
    }

    fetchNextScenes() {
        this.fetchingScenes = true;
        this.fetchError = false;
        this.hasNext = false;
        this.sceneCount = null;
        let factoryFn = this.bboxFetchFactory;
        let fetchScenes = this.fetchNextScenesForBbox;
        this.fetchNextScenesForBbox().then(
            ({ scenes, hasNext, count }) => {
                if (
                    fetchScenes === this.fetchNextScenesForBbox &&
                    factoryFn === this.bboxFetchFactory
                ) {
                    this.fetchingScenes = false;
                    this.fetchError = false;
                    this.sceneList = this.sceneList.concat(scenes);
                    this.sceneActions = this.sceneActions.merge(
                        new Map(scenes.map(this.addSceneActions.bind(this)))
                    );
                    this.hasNext = hasNext;
                    this.sceneCount = count;
                }
            },
            err => {
                if (
                    fetchScenes === this.fetchNextScenesForBbox &&
                    factoryFn === this.bboxFetchFactory
                ) {
                    this.fetchingScenes = false;
                    if (err !== 'No more scenes to fetch') {
                        this.fetchError = err;
                    }
                    this.hasNext = false;
                }
            }
        );
    }

    addSceneActions(scene) {
        // details, view layers, hide (unapprove), remove (delete from layer)
        let actions = [
            {
                icons: [
                    {
                        icon: 'icon-eye',
                        isActive: () => this.visibleScenes.has(scene.id)
                    },
                    {
                        icon: 'icon-eye-off',
                        isActive: () => !this.visibleScenes.has(scene.id)
                    }
                ],
                name: 'Preview',
                tooltip: 'Show image boundaries on map',
                callback: () => this.toggleVisibleScene(scene),
                menu: false
            }
        ];

        if (this.hasDownloadPermission(scene)) {
            actions.unshift({
                icon: 'icon-download',
                name: 'Download',
                tooltip: 'Download raw image data',
                callback: () =>
                    this.modalService
                        .open({
                            component: 'rfSceneDownloadModal',
                            resolve: {
                                scene: () => scene
                            }
                        })
                        .result.catch(() => {}),
                menu: false
            });
        } else {
            actions.unshift({
                icon: 'icon-item-lock',
                name:
                    'This imagery requires additional access ' +
                    `${this.currentRepository.service.permissionSource}`,
                title: 'Download raw image data',
                menu: false
            });
        }

        return [scene.id, actions];
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

    onRepositoryChange(bboxFetchFactory, repository) {
        this.bboxFetchFactory = bboxFetchFactory;
        this.currentRepository = repository;

        this.sceneList = [];
        this.sceneActions = new Map();
        this.getMap().then(mapWrapper => {
            if (this.bboxCoords || (this.queryParams && this.queryParams.bbox)) {
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
        this.zoom = zoom;
        this.center = newCenter;

        this.setBboxParam(this.bboxCoords);

        this.onBboxChange();
    }

    onBboxChange() {
        let queryParams = this.$location.search();
        if (this.bboxFetchFactory && !queryParams.shape) {
            this.fetchNextScenesForBbox = this.bboxFetchFactory(this.bboxCoords);
            this.sceneList = [];
            this.sceneActions = new Map();
            this.debouncedSceneFetch();
        }
    }

    isSelected(scene) {
        return this.selected.has(scene.id);
    }

    onSelect(scene) {
        if (this.selected.has(scene.id)) {
            this.selected = this.selected.delete(scene.id);
        } else {
            this.selected = this.selected.set(scene.id, scene);
        }
        this.updateSelectText();
    }

    allVisibleSelected() {
        let sceneSet = new Set(this.sceneList.map(s => s.id).filter(s => !s.inLayer));
        return (
            this.selected.size &&
            this.selected
                .keySeq()
                .toSet()
                .intersect(sceneSet).size === sceneSet.size
        );
    }

    selectAll() {
        if (this.allVisibleSelected()) {
            this.selected = this.selected.clear();
        } else {
            this.selected = this.selected.merge(
                _.filter(
                    this.sceneList.map(s => [s.id, s]),
                    s => !s.inLayer
                )
            );
        }
        this.updateSelectText();
    }

    updateSelectText() {
        if (this.allVisibleSelected()) {
            this.selectText = `Clear selected (${this.selected.size})`;
        } else {
            this.selectText = `Select all listed (${this.selected.size})`;
        }
    }

    hasDownloadPermission(scene) {
        if (this.currentRepository.service.getScenePermissions(scene).includes('download')) {
            return true;
        }
        return false;
    }

    isInProject(scene) {
        return scene && scene.inProject;
    }

    isInLayer(scene) {
        return scene && scene.inLayer;
    }

    addScenesToLayer() {
        // add spinner to all scenes which are in this.scenesBeingAdded
        let addedScenes = this.selected.filterNot(scene => this.scenesBeingAdded.has(scene.id));
        let addedSceneIds = addedScenes.keySeq().toSet();
        this.scenesBeingAdded = this.scenesBeingAdded.union(addedSceneIds);
        this.currentAddRequest = this.currentRepository.service
            .addToLayer(this.project.id, this.layer.id, addedScenes.valueSeq().toArray())
            .then(
                () => {
                    this.scenesBeingAdded = this.scenesBeingAdded.subtract(addedSceneIds);
                    this.selected = this.selected.filterNot((s, id) => addedSceneIds.has(id));
                    this.sceneList
                        .filter(s => addedSceneIds.has(s.id))
                        .forEach(s => {
                            s.inProject = true;
                            s.inLayer = true;
                        });
                    this.removeMapLayers().then(() => this.setMapLayers());
                },
                e => {
                    this.scenesBeingAdded.subtract(addedSceneIds);
                    this.errorAddingScenes =
                        'There was an error adding scenes to the layer. Please try again.';
                    this.$timeout(() => {
                        delete this.errorAddingScenes;
                    }, 5);
                }
            );
    }

    toggleVisibleScene(scene) {
        if (this.visibleScenes.has(scene.id)) {
            this.visibleScenes = this.visibleScenes.delete(scene.id);
        } else {
            this.visibleScenes = this.visibleScenes.set(scene.id, scene);
        }

        this.syncVisibleScenes();
    }

    syncVisibleScenes() {
        this.getMap().then(map => {
            // find scenes to be removed
            // find scenes to be added
            let newSceneIds = this.visibleScenes.keySeq().toSet();
            let removedSceneIds = this.scenesOnMap.subtract(newSceneIds);
            let addedScenes = this.visibleScenes.filterNot(s => this.scenesOnMap.has(s.id));
            removedSceneIds.forEach(id => {
                map.deleteGeojson(id);
            });
            addedScenes.forEach(scene => {
                map.setGeojson(scene.id, this.sceneService.getStyledFootprint(scene));
            });
            this.scenesOnMap = newSceneIds;
        });
    }
}

const component = {
    bindings: {
        project: '<',
        layer: '<'
    },
    templateUrl: tpl,
    controller: LayerScenesBrowseController.name
};

export default angular
    .module('components.pages.project.layers.scenes.browse', [])
    .controller(LayerScenesBrowseController.name, LayerScenesBrowseController)
    .component('rfProjectLayerScenesBrowsePage', component).name;

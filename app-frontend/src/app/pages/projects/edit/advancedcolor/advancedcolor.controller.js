import {Map} from 'immutable';

export default class ProjectsAdvancedColorController {
    constructor( // eslint-disable-line max-params
        $log, $scope, $q, projectService, layerService, sceneService, $state, mapService,
        datasourceService, mapUtilsService, colorCorrectService, projectEditService,
        RasterFoundryRepository
    ) {
        'ngInject';
        this.projectService = projectService;
        this.layerService = layerService;
        this.sceneService = sceneService;
        this.datasourceService = datasourceService;
        this.mapUtilsService = mapUtilsService;
        this.colorCorrectService = colorCorrectService;
        this.projectEditService = projectEditService;
        this.$state = $state;
        this.$scope = $scope;
        this.$parent = $scope.$parent.$ctrl;
        this.$q = $q;
        this.$log = $log;
        this.repository = {
            name: 'Raster Foundry',
            service: RasterFoundryRepository
        };
        this.getMap = () => mapService.getMap('edit');
    }

    $onInit() {
        this.selectedTileX = null;
        this.selectedTileY = null;
        this.selectedScenes = new Map();
        this.selectedLayers = new Map();
        if (this.$parent.project) {
            this.project = this.$parent.project;
            this.initMap();
        } else {
            this.$parent.waitForProject().then((project) => {
                this.project = project;
                this.initMap();
            });
        }

        this.$scope.$on('$destroy', this.$onDestroy.bind(this));
    }

    $onDestroy() {
        this.getMap().then((map) => {
            this.listeners.forEach((listener) => {
                map.off(listener);
            });
            map.deleteLayers('grid-select');
            map.deleteLayers('grid-selection-layer');
            this.selectedScenes.forEach((scene) => map.deleteGeojson(scene.id));
        });
    }

    initMap() {
        this.gridLayer = L.gridLayer({
            tileSize: 64,
            className: 'grid-select'
        });
        this.gridLayer.setZIndex(100);

        this.getMap().then((map) => {
            this.listeners = [
                map.on('click', this.selectGridCellScenes.bind(this)),
                map.on('contextmenu', (evt, src) => {
                    evt.originalEvent.preventDefault();
                    if (evt.originalEvent.ctrlKey) {
                        this.selectGridCellScenes(evt, src);
                    }
                    return false;
                })
            ];
            map.addLayer('grid-selection-layer', this.gridLayer);
            this.selectedScenes.forEach((scene) => {
                map.setGeojson(scene.id, this.sceneService.getStyledFootprint(scene));
            });
            this.mosaic = () => this.$parent.mosaicLayer.values().next().value;
        });
    }

    /**
     * Select the scenes that fall within the clicked grid cell
     *
     * @param {object} $event from the map
     * @param {MapWrapper} source event source
     * @returns {undefined}
     */
    selectGridCellScenes($event, source) {
        if (this.$state.includes('^.adjust')) {
            return;
        }
        let multi = $event.originalEvent.ctrlKey;
        // Cache current request kickoff time for identifying request
        let requestTime = new Date();

        // this.lastRequest will always hold the latest kickoff time
        this.lastRequest = requestTime;

        if (!this.filterBboxList || !this.filterBboxList.length) {
            this.filterBboxList = [];
        }


        let z = source.map.getZoom();

        // The conversion functions above return the upper-left (northwest) corner of the tile,
        // so to get the lower left and upper right corners to make a bounding box, we need to
        // get the upper-left corners of the tiles directly below and to the right of this one.
        let tx = this.mapUtilsService.lng2Tile($event.latlng.lng, z);
        let ty = this.mapUtilsService.lat2Tile($event.latlng.lat, z);

        let bounds = L.latLngBounds([
            [this.mapUtilsService.tile2Lat(ty, z), this.mapUtilsService.tile2Lng(tx + 1, z)],
            [this.mapUtilsService.tile2Lat(ty + 1, z), this.mapUtilsService.tile2Lng(tx, z)]
        ]);

        let filteredList = this.filterBboxList.filter(b => {
            return !b.equals(bounds) && !b.contains(bounds) && !bounds.contains(b);
        });

        if (filteredList.length === this.filterBboxList.length) {
            // If the clicked bounding box has not been selected
            if (!multi) {
                this.filterBboxList = [];
            }
            this.filterBboxList.push(bounds);
        } else if (!multi) {
            // If the clicked bounding box is already selected
            this.filterBboxList = [];
        } else {
            this.filterBboxList = filteredList;
        }

        this.updateGridSelection();

        if (this.filterBboxList.length) {
            this.projectService.getAllProjectScenes({
                projectId: this.project.id,
                bbox: this.filterBboxList.map(r => r.toBBoxString()).join(';')
            }).then(({scenes: selectedScenes}) => {
                if (this.lastRequest === requestTime) {
                    this.selectNoScenes();
                    selectedScenes.map((scene) => this.setSelected(scene, true));
                }
            });
        } else {
            this.selectNoScenes();
        }
    }

    onToggleSelection() {
        if (this.shouldSelectAll()) {
            this.selectAllScenes();
        } else {
            this.selectNoScenes();
        }
    }

    shouldSelectAll() {
        return this.selectedScenes.size === 0 ||
            this.selectedScenes.size < this.$parent.sceneList.length;
    }

    setSelected(scene, selected) {
        if (selected) {
            this.selectedScenes = this.selectedScenes.set(scene.id, scene);
            this.selectedLayers = this.selectedLayers.set(
                scene.id, this.layerService.layerFromScene(scene, this.$parent.projectId)
            );
            this.getMap().then((map) => {
                map.setGeojson(scene.id, this.sceneService.getStyledFootprint(scene));
            });
        } else {
            this.selectedScenes = this.selectedScenes.delete(scene.id);
            this.selectedLayers = this.selectedLayers.delete(scene.id);
            this.getMap().then((map) => {
                map.deleteGeojson(scene.id);
            });
        }
        let firstLayer = this.selectedLayers.values().next().value;
        if (firstLayer) {
            firstLayer.getColorCorrection().then(this.setCorrection.bind(this));
        }
    }

    selectAllScenes() {
        this.$parent.sceneList.map((scene) => this.setSelected(scene, true));
    }

    selectNoScenes() {
        this.selectedScenes.forEach((scene) => this.setSelected(scene, false));
        this.$scope.$evalAsync();
    }

    updateGridSelection() {
        if (!this.gridSelectionLayer) {
            this.gridSelectionLayer = L.featureGroup();
            this.getMap().then((map) => {
                map.addLayer('grid-selection-layer', this.gridSelectionLayer);
            });
        } else {
            this.gridSelectionLayer.clearLayers();
        }
        this.filterBboxList.forEach(b => L.rectangle(b).addTo(this.gridSelectionLayer));
    }

    isSelected(scene) {
        return this.selectedScenes.has(scene.id);
    }

    /**
     * Triggered when the adjustment pane reports changes to color correction
     *
     * Applies color corrections to all selected layers
     * @param {object} newCorrection object to apply to each layer
     *
     * @returns {null} null
     */
    onCorrectionChange(newCorrection) {
        const sceneIds = Array.from(this.selectedScenes.keys());

        if (newCorrection) {
            const promise = this.colorCorrectService.bulkUpdate(
                this.projectEditService.currentProject.id,
                sceneIds,
                newCorrection
            );
            return this.redrawMosaic(promise);
        }
        return this.$q.reject();
    }


    resetCorrection() {
        const sceneIds = Array.from(this.selectedScenes.keys());
        const promise = this.colorCorrectService.bulkUpdate(
            this.projectEditService.currentProject.id,
            sceneIds
        );
        const defaultCorrection = this.colorCorrectService.getDefaultColorCorrection();
        this.setCorrection(defaultCorrection);
        this.redrawMosaic(promise, defaultCorrection);
    }

    setCorrection(correction) {
        this.correction = correction;
    }

    redrawMosaic(promise) {
        return promise.then(() => {
            this.$parent.layerFromProject();
        });
    }
}

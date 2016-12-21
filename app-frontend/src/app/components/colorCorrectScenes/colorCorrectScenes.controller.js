export default class ColorCorrectScenesController {
    constructor( // eslint-disable-line max-params
        $log, $scope, $q, projectService, layerService, $state, mapService
    ) {
        'ngInject';
        this.projectService = projectService;
        this.layerService = layerService;
        this.$state = $state;
        this.$q = $q;
        this.getMap = () => mapService.getMap('project');
    }

    $onInit() {
        // Internal bookkeeping to handle grid selection functionality.
        this.selectedTileX = null;
        this.selectedTileY = null;
        this.selectingScenes = false;
        this.projectid = this.$state.params.projectid;

        this.getMap().then((map) => {
            this.listeners = [
                map.on('click', this.selectGridCellScenes.bind(this))
            ];
        });
    }

    $onDestroy() {
        this.getMap().then((map) => {
            this.listeners.forEach((listener) => {
                map.off(listener);
            });
            map.deleteLayers('highlight');
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
        // Helper functions for converting between click Lat/Lng and tile numbers
        // From http://wiki.openstreetmap.org/wiki/Slippy_map_tilenames
        function lng2Tile(lng, zoom) {
            return Math.floor((lng + 180) / 360 * Math.pow(2, zoom));
        }

        function lat2Tile(lat, zoom) {
            return Math.floor((1 - Math.log(Math.tan(lat * Math.PI / 180) +
                                            1 / Math.cos(lat * Math.PI / 180))
                                   / Math.PI) / 2 * Math.pow(2, zoom));
        }

        function tile2Lng(x, zoom) {
            return x / Math.pow(2, zoom) * 360 - 180;
        }

        function tile2Lat(y, zoom) {
            let n = Math.PI - 2 * Math.PI * y / Math.pow(2, zoom);
            return 180 / Math.PI * Math.atan(0.5 * (Math.exp(n) - Math.exp(-n)));
        }

        if (this.selectingScenes || this.loading) {
            return;
        }

        let zoom = source.map.getZoom();
        // The conversion functions above return the upper-left (northwest) corner of the tile,
        // so to get the lower left and upper right corners to make a bounding box, we need to
        // get the upper-left corners of the tiles directly below and to the right of this one.
        let tileX = lng2Tile($event.latlng.lng, zoom);
        let tileY = lat2Tile($event.latlng.lat, zoom);
        if (tileX !== this.selectedTileX || tileY !== this.selectedTileY ||
            zoom !== this.selectedTileZ) {
            this.selectedTileX = tileX;
            this.selectedTileY = tileY;
            this.selectedTileZ = zoom;

            let llLat = tile2Lat(tileY + 1, zoom);
            let llLng = tile2Lng(tileX, zoom);
            let urLat = tile2Lat(tileY, zoom);
            let urLng = tile2Lng(tileX + 1, zoom);
            let bboxString = `${llLng},${llLat},${urLng},${urLat}`;
            this.selectingScenes = true;
            this.projectService.getAllProjectScenes({
                projectId: this.projectid,
                bbox: bboxString
            }).then((selectedScenes) => {
                this.selectNoScenes();
                selectedScenes.map((scene) => this.setSelected(scene, true));
                this.selectingScenes = false;
            });

            // While the scene selection is in progress, put a highlight on the map so the user can
            // see where they've clicked.
            this.updateGridSelection(
                L.latLng({lat: llLat, lng: llLng}),
                L.latLng({lat: urLat, lng: urLng})
            );
        } else {
            // We've clicked on the same square that was already selected; toggle
            this.selectNoScenes();
            this.clearGridHighlight();
        }
    }

    /**
     * Set RGB bands for layers
     *
     * TODO: Only works for Landsat8 -- needs to be adjusted based on datasource
     *
     * @param {string} bandName name of band selected
     *
     * @returns {null} null
     */
    setBands(bandName) {
        let bands = {
            natural: {red: 3, green: 2, blue: 1},
            cir: {red: 4, green: 3, blue: 2},
            urban: {red: 6, green: 5, blue: 4},
            water: {red: 4, green: 5, blue: 3},
            atmosphere: {red: 6, green: 4, blue: 2},
            agriculture: {red: 5, green: 4, blue: 1},
            forestfire: {red: 6, green: 4, blue: 1},
            bareearth: {red: 5, green: 2, blue: 1},
            vegwater: {red: 4, green: 6, blue: 0}
        };

        this.sceneLayers.forEach(function (layer) {
            layer.updateBands(bands[bandName]);
        });
    }

    onToggleSelection() {
        // This fires pre-change, so if the box is checked then we need to deselect
        if (this.shouldSelectAll()) {
            this.selectAllScenes();
        } else {
            this.selectNoScenes();
        }
    }

    shouldSelectAll() {
        return this.selectedScenes.size === 0 || this.selectedScenes.size < this.sceneList.size;
    }

    clearGridHighlight() {
        this.selectedTileX = null;
        this.selectedTileY = null;
        this.getMap().then((map) => {
            map.deleteLayers('highlight');
        });
    }

    selectAllScenes() {
        this.sceneList.map((scene) => {
            this.selectedScenes.set(scene.id, scene);
            this.selectedLayers.set(scene.id, this.sceneLayers.get(scene.id));
        });
    }

    selectNoScenes() {
        this.selectedScenes.clear();
        this.selectedLayers.clear();
    }

    isSelected(scene) {
        return this.selectedScenes.has(scene.id);
    }

    setSelected(scene, selected) {
        if (selected) {
            this.selectedScenes.set(scene.id, scene);
            this.selectedLayers.set(scene.id, this.sceneLayers.get(scene.id));
        } else {
            this.selectedScenes.delete(scene.id);
            this.selectedLayers.delete(scene.id);
        }
    }

    setHoveredScene(scene) {
        this.onSceneMouseover({scene: scene});
    }

    removeHoveredScene() {
        this.onSceneMouseleave();
    }

    updateGridSelection(swPoint, nePoint) {
        let newGridHighlight = L.rectangle([swPoint, nePoint]);
        this.getMap().then((map) => {
            map.setLayer('highlight', newGridHighlight);
        });
    }
}

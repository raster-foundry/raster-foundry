export default class ColorCorrectScenesController {
    constructor( // eslint-disable-line max-params
        $log, $scope, $q, projectService, layerService, $state
    ) {
        'ngInject';
        this.projectService = projectService;
        this.layerService = layerService;
        this.$state = $state;
        this.$q = $q;
    }

    $onInit() {
        this.project = this.$state.params.project;
        this.projectId = this.$state.params.projectid;
        // Internal bookkeeping to handle grid selection functionality.
        this.selectedTileX = null;
        this.selectedTileY = null;
        this.gridHighlight = null;
        this.selectingScenes = false;

        // Populate project scenes
        if (!this.project) {
            if (this.projectId) {
                this.loading = true;
                this.projectService.query({id: this.projectId}).then(
                    (project) => {
                        this.project = project;
                        this.loading = false;
                        this.populateSceneList();
                    },
                    () => {
                        this.$state.go('library.projects.list');
                    }
                );
            } else {
                this.$state.go('library.projects.list');
            }
        } else {
            this.populateSceneList();
        }
    }

    $onChanges(changesObj) {
        if (changesObj.clickEvent && changesObj.clickEvent.currentValue && this.mapZoom) {
            this.selectGridCellScenes(changesObj.clickEvent.currentValue);
        }
    }

    populateSceneList() {
        // If we are returning from a different state that might preserve the
        // sceneList, like the color correction adjustments, then we don't need
        // to re-request scenes.
        if (this.loading || this.sceneList.length > 0) {
            this.layersFromScenes();
            return;
        }

        delete this.errorMsg;
        this.loading = true;
        // save off selected scenes so you don't lose them during the refresh
        this.sceneList = [];
        this.projectService.getAllProjectScenes({projectId: this.project.id}).then(
            (allScenes) => {
                this.sceneList = allScenes;
                this.layersFromScenes();
            },
            () => {
                this.errorMsg = 'Error loading scenes.';
            }).finally(() => {
                this.loading = false;
            }
        );
    }

    layersFromScenes() {
        // Create scene layers to use for color correction
        for (const scene of this.sceneList) {
            let sceneLayer = this.layerService.layerFromScene(scene);
            this.sceneLayers.set(scene.id, sceneLayer);
        }
        this.layers = this.sceneLayers.values();
    }

    /**
     * Select the scenes that fall within the clicked grid cell
     *
     * @param {object} clickEvent from the map
     * @returns {undefined}
     */
    selectGridCellScenes(clickEvent) {
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
        let zoom = this.mapZoom;
        // The conversion functions above return the upper-left (northwest) corner of the tile,
        // so to get the lower left and upper right corners to make a bounding box, we need to
        // get the upper-left corners of the tiles directly below and to the right of this one.
        let tileX = lng2Tile(clickEvent.latlng.lng, zoom);
        let tileY = lat2Tile(clickEvent.latlng.lat, zoom);
        if (tileX !== this.selectedTileX || tileY !== this.selectedTileY) {
            this.selectedTileX = tileX;
            this.selectedTileY = tileY;

            let llLat = tile2Lat(tileY + 1, zoom);
            let llLng = tile2Lng(tileX, zoom);
            let urLat = tile2Lat(tileY, zoom);
            let urLng = tile2Lng(tileX + 1, zoom);
            let bboxString = `${llLng},${llLat},${urLng},${urLat}`;
            this.selectingScenes = true;
            this.projectService.getAllProjectScenes({
                projectId: this.project.id,
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


    selectNoScenes() {
        this.selectedScenes.clear();
        this.selectedLayers.clear();
    }

    clearGridHighlight() {
        this.selectedTileX = null;
        this.selectedTileY = null;
        this.gridHighlight = null;
        this.newGridSelection({highlight: this.gridHighlight});
    }

    selectAllScenes() {
        this.sceneList.map((scene) => {
            this.selectedScenes.set(scene.id, scene);
            this.selectedLayers.set(scene.id, this.sceneLayers.get(scene.id));
        });
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
        this.gridHighlight = newGridHighlight;
        this.newGridSelection({highlight: this.gridHighlight});
    }
}

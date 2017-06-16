/* globals L */
export default class DrawAoiController {
    constructor($log, $state, $scope, mapService) {
        'ngInject';

        this.$log = $log;
        this.$state = $state;
        this.$scope = $scope;
        this.$parent = $scope.$parent;

        this.getMap = () => mapService.getMap('edit');
    }

    $onInit() {
        this.getMap().then((map) => {
            this.$scope.$on('$destroy', this.$onDestroy.bind(this));
            this.listeners = [
                map.on(L.Draw.Event.CREATED, this.addAOI.bind(this)),
                map.on(L.Draw.Event.EDITED, this.editAOI.bind(this)),
                map.on(L.Draw.Event.DELETED, this.deleteAOI.bind(this))
            ];

            this.aoiLayers = [];


            this.watches = [this.$parent.$watch('$ctrl.aoiPolygons', (polygons) => {
                // parent scope should not set polygons with data more than once:
                // set once during controller initialization, then any time after when
                // this scope is exited

                let drawLayer = L.geoJSON(polygons && polygons.length ? polygons : null, {
                    style: () => {
                        return {
                            weight: 2,
                            fillOpacity: 0.2
                        };
                    }
                });

                map.addLayer('draw', drawLayer);

                /* eslint-disable no-underscore-dangle */
                Object.keys(drawLayer._layers).forEach((layer) => {
                    this.aoiLayers.push(drawLayer._layers[layer]);
                });
                /* eslint-enable no-underscore-dangle */

                this.drawHandler = new L.Draw.Polygon(map.map, {
                    allowIntersection: false,
                    shapeOptions: {
                        weight: 2,
                        fillOpacity: 0.2
                    }
                });

                this.editHandler = new L.EditToolbar.Edit(map.map, {
                    featureGroup: drawLayer,
                    // TODO figure out why this option doesn't work
                    allowIntersection: false
                });

                this.deleteHandler = new L.EditToolbar.Delete(map.map, {
                    featureGroup: drawLayer
                });
            })];
        });
    }

    $onDestroy() {
        // clear watches
        this.watches.forEach((watch) => {
            watch();
        });
        // clear map listeners
        this.getMap().then((map) => {
            this.listeners.forEach((listener) => {
                map.off(listener);
            });
            map.deleteLayers('draw');
        });
    }

    updateAOIs() {
        // Coordinates aren't actually wound counter-clockwise,
        // but our backend doesn't care
        let polygons = this.aoiLayers.map((layer) => {
            let feature = layer.toGeoJSON();
            let coordinates = feature.geometry.coordinates;
            return coordinates;
        });

        this.$parent.$ctrl.updateProjectAOIs(polygons);
    }

    onDoneClicked() {
        this.updateAOIs();
        this.$state.go('^');
    }

    revertLayers() {
        if (this.drawing) {
            this.toggleDrawing();
        }
        if (this.editing) {
            this.toggleEditing(false);
        }
        if (this.deleting) {
            this.toggleDeleting(false);
        }
    }

    toggleDrawing() {
        this.drawing = !this.drawing;
        if (this.drawing) {
            if (this.editing) {
                this.toggleEditing(false);
            } else if (this.deleting) {
                this.toggleDeleting(false);
            }

            this.drawHandler.enable();
        } else {
            this.drawHandler.disable();
        }
    }

    toggleEditing(save) {
        this.editing = !this.editing;

        if (this.editing) {
            if (this.drawing) {
                this.toggleDrawing();
            } else if (this.deleting) {
                this.toggleDeleting(false);
            }

            this.editHandler.enable();
        } else if (save) {
            this.editHandler.disable();
        } else {
            this.editHandler.revertLayers();
            this.editHandler.disable();
        }
    }

    toggleDeleting(save) {
        this.deleting = !this.deleting;

        if (this.deleting) {
            if (this.drawing) {
                this.toggleDrawing();
            } else if (this.editing) {
                this.toggleEditing(false);
            }
            this.deleteHandler.enable();
        } else if (save) {
            this.deleteHandler.save();
            this.deleteHandler.disable();
        } else {
            this.deleteHandler.revertLayers();
            this.deleteHandler.disable();
        }
    }

    saveChanges() {
        if (this.editing) {
            this.toggleEditing(true);
        } else if (this.deleting) {
            this.toggleDeleting(true);
        }
    }

    clearStates() {
        if (this.drawing) {
            this.toggleDrawing();
        } else if (this.editing) {
            this.toggleEditing(false);
        } else if (this.deleting) {
            this.toggleDeleting(false);
        }
    }

    addAOI(event) {
        this.drawing = false;
        let layer = event.layer;
        layer.properties = {
            area: this.calculateArea(layer),
            id: new Date()
        };
        this.getMap().then((map) => {
            let drawLayer = map.getLayers('draw')[0];
            drawLayer.addLayer(layer);
            this.aoiLayers.push(layer);
            this.$scope.$evalAsync();
        });
    }

    /* eslint-disable no-underscore-dangle */
    editAOI(event) {
        Object.values(
            event.layers._layers
        ).forEach((layer) => {
            layer.properties.area = this.calculateArea(layer);
        });
        this.$scope.$evalAsync();
    }

    deleteAOI(event) {
        Object.keys(event.layers._layers).forEach((layerid) => {
            let layer = event.layers._layers[layerid];
            this.aoiLayers.splice(this.aoiLayers.indexOf(layer), 1);
        });
        this.$scope.$evalAsync();
    }
    /* eslint-enable no-underscore-dangle */

    calculateArea(layer) {
        return L.GeometryUtil.geodesicArea(layer.getLatLngs()[0]) / 1000000;
    }
}

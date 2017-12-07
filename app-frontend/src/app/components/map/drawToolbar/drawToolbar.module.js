/* globals L */
import angular from 'angular';
require('./drawToolbar.scss');
import drawToolbarTpl from './drawToolbar.html';

/*
  @param {string} mapId id of map to use
  @param {object?} options optional object with options for the toolbar
                   {
                   areaType: text describing type of polygons being drawn
                   requirePolygons: boolean, whether to allow saving with no polygons
                   }
  @param {object?} geom geometry containing multipolygon
                  {
                    geom: {type: 'MultiPolygon', coords: [...]}
                    srid: '...'
                  }
  @param {function(geometry: Object)} onSave function to call when the save button is clicked.
                                             Object has same format as the geom property
  @param {function()} onCancel function to call when the cancel button is clicked
 */
const DrawToolbarComponent = {
    templateUrl: drawToolbarTpl,
    controller: 'DrawToolbarController',
    bindings: {
        mapId: '@',
        options: '<?',
        geom: '<?',
        onSave: '&',
        onCancel: '&'
    }
};

class DrawToolbarController {
    constructor($log, $scope, mapService) {
        'ngInject';

        this.$log = $log;
        this.$scope = $scope;

        this.getMap = () => mapService.getMap(this.mapId);
    }

    $onInit() {
        this.polygonLayers = [];
        this.areaTypeText = '';


        this.getMap().then((mapWrapper) => {
            this.listeners = [
                mapWrapper.on(L.Draw.Event.CREATED, this.addPolygon.bind(this)),
                mapWrapper.on(L.Draw.Event.EDITED, this.editPolygon.bind(this)),
                mapWrapper.on(L.Draw.Event.DELETED, this.deletePolygon.bind(this))
            ];

            if (!mapWrapper.getLayers('draw').length) {
                this.setDrawLayer(mapWrapper);
            }
        });

        this.$scope.$on('$destroy', this.$onDestroy.bind(this));
    }

    setDrawLayer(mapWrapper, polygons) {
        let drawLayer = L.geoJSON(polygons ? polygons : null, {
            style: () => {
                return {
                    weight: 2,
                    fillOpacity: 0.2
                };
            }
        });

        mapWrapper.setLayer('draw', drawLayer, false);

        this.polygonLayers = [];
        /* eslint-disable no-underscore-dangle */
        Object.keys(drawLayer._layers).forEach((layer) => {
            this.polygonLayers.push(drawLayer._layers[layer]);
        });
        /* eslint-enable no-underscore-dangle */

        this.drawHandler = new L.Draw.Polygon(mapWrapper.map, {
            allowIntersection: false,
            shapeOptions: {
                weight: 2,
                fillOpacity: 0.2
            }
        });

        this.editHandler = new L.EditToolbar.Edit(mapWrapper.map, {
            featureGroup: drawLayer,
            // TODO figure out why this option doesn't work
            allowIntersection: false
        });

        this.deleteHandler = new L.EditToolbar.Delete(mapWrapper.map, {
            featureGroup: drawLayer
        });
    }

    $onDestroy() {
        // clear map listeners
        this.getMap().then((mapWrapper) => {
            this.listeners.forEach((listener) => {
                mapWrapper.off(listener);
            });
            mapWrapper.deleteLayers('draw');
        });
    }

    $onChanges(changes) {
        if (changes.options && changes.options.currentValue) {
            this.options = changes.options.currentValue;
        }

        if (changes.geom && changes.geom.currentValue) {
            let geom = changes.geom.currentValue.geom;
            if (!geom || !geom.coordinates) {
                this.$log.error('Invalid geometry used in draw toolbar');
            } else {
                // split multipolygon into polygons
                let polygons = geom.coordinates.map((coords) => {
                    return {
                        type: 'Polygon',
                        coordinates: coords
                    };
                });
                if (polygons.length) {
                    this.getMap().then((mapWrapper) => {
                        this.setDrawLayer(mapWrapper, polygons);
                    });
                }
            }
        }
    }

    updatePolygons() {
        // Coordinates aren't actually wound counter-clockwise,
        // but our backend doesn't care
        let polygons = this.polygonLayers.map((layer) => {
            let feature = layer.toGeoJSON();
            let coordinates = feature.geometry.coordinates;
            return coordinates;
        });

        this.$parent.$ctrl.updateProjectAOIs(polygons);
    }

    getMultiPolygonFromLayers() {
        if (this.polygonLayers.length) {
            return {
                geom: {
                    type: 'MultiPolygon',
                    coordinates: this.polygonLayers.map((layer) => {
                        let feature = layer.toGeoJSON();
                        return feature.geometry.coordinates;
                    })
                },
                srid: 3857
            };
        }
        return null;
    }

    onDoneClicked() {
        this.onSave({polygons: this.getMultiPolygonFromLayers()});
    }

    onCancelClicked() {
        this.onCancel();
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

    addPolygon(event) {
        this.drawing = false;
        let layer = event.layer;
        layer.properties = {
            area: this.calculateArea(layer),
            id: new Date()
        };
        this.getMap().then((mapWrapper) => {
            let drawLayer = mapWrapper.getLayers('draw')[0];
            drawLayer.addLayer(layer);
            this.polygonLayers.push(layer);
            this.$scope.$evalAsync();
        });
    }

    /* eslint-disable no-underscore-dangle */
    editPolygon(event) {
        Object.values(
            event.layers._layers
        ).forEach((layer) => {
            layer.properties.area = this.calculateArea(layer);
        });
        this.$scope.$evalAsync();
    }

    deletePolygon(event) {
        Object.keys(event.layers._layers).forEach((layerid) => {
            let layer = event.layers._layers[layerid];
            this.polygonLayers.splice(this.polygonLayers.indexOf(layer), 1);
        });
        this.$scope.$evalAsync();
    }
    /* eslint-enable no-underscore-dangle */

    calculateArea(layer) {
        return L.GeometryUtil.geodesicArea(layer.getLatLngs()[0]) / 1000000;
    }
}

const DrawToolbarModule = angular.module('components.map.drawToolbar', []);

DrawToolbarModule.component('rfDrawToolbar', DrawToolbarComponent);
DrawToolbarModule.controller('DrawToolbarController', DrawToolbarController);

export default DrawToolbarModule;

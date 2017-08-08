/* globals L */
const _ = require('lodash');

export default class AnnotateToolbarController {
    constructor(
        $log, $scope, $compile,
        mapService
    ) {
        'ngInject';
        this.$log = $log;
        this.$scope = $scope;
        this.$compile = $compile;
        this.getMap = () => mapService.getMap(this.mapId);
    }

    $onInit() {
        this.getMap().then((mapWrapper) => {
            this.listeners = [
                mapWrapper.on(L.Draw.Event.CREATED, this.addShape.bind(this))
            ];
            this.setDrawHandlers(mapWrapper);
        });
        this.labelInputs = [];
        this.labelOriginalObject = {};
    }

    $onDestroy() {
        this.getMap().then((mapWrapper) => {
            this.listeners.forEach((listener) => {
                mapWrapper.off(listener);
            });
            mapWrapper.deleteLayers('Annotation');
        });
    }

    setDrawHandlers(mapWrapper) {
        this.drawRectangleHandler = new L.Draw.Rectangle(mapWrapper.map, {
            shapeOptions: {
                weight: 2,
                fillOpacity: 0.2
            }
        });
        this.drawPolygonHandler = new L.Draw.Polygon(mapWrapper.map, {
            allowIntersection: false,
            shapeOptions: {
                weight: 2,
                fillOpacity: 0.2
            }
        });
        this.drawMarkerHandler = new L.Draw.Marker(mapWrapper.map, {
            icon: L.divIcon({'className': 'annotate-marker'})
        });
    }

    toggleDrawing(shapeType) {
        if (shapeType === 'rectangle') {
            this.drawRectangleHandler.enable();
            this.drawPolygonHandler.disable();
            this.drawMarkerHandler.disable();
        } else if (shapeType === 'polygon') {
            this.drawPolygonHandler.enable();
            this.drawRectangleHandler.disable();
            this.drawMarkerHandler.disable();
        } else {
            this.drawMarkerHandler.enable();
            this.drawRectangleHandler.disable();
            this.drawPolygonHandler.disable();
        }
    }

    addShape(e) {
        let layer = e.layer;
        let compiled = this.makePopup(layer);
        layer.bindPopup(compiled[0], {
            'closeButton': false
        });
        this.getMap().then((mapWrapper) => {
            mapWrapper.addLayer('Annotation', layer, true);
            layer.openPopup();
        });
    }

    makePopup(layer) {
        let popupScope = this.$scope.$new();
        popupScope.allLabels = this.labelInputs;
        let popupContent = angular.element(
            `
            <form novalidate class="simple-form">
                <label class="leaflet-popup-label">Label: <br/>
                <div angucomplete-alt
                    pause="100"
                    selected-object="labelObj"
                    local-data="allLabels"
                    search-fields="name"
                    title-field="name"
                    minlength="1"
                    input-class="form-control ng-pristine ng-untouched ng-valid ng-empt"
                    match-class="highlight"
                    override-suggestions="true"/>
                </div></label><br/>
                <label class="leaflet-popup-label">Description: <br/>
                <textarea class="form-control ng-pristine ng-untouched ng-valid ng-empty"
                    ng-model="description"></textarea></label><br/>
                <input type="button" class="btn btn-light"
                    ng-click="cancelAnnotation()" value="Cancel" />
                <input type="submit" class="btn btn-tertiary" style="float: right;"
                    ng-click="addAnnotation(labelObj, description)" value="Add" />
            </form>
            `
        );
        popupScope.cancelAnnotation = () => {
            this.deleteLabelAndLayer(layer);
        };
        popupScope.addAnnotation = (labelObj, description) => {
            let label = this.getLabelFromAutocomplete(labelObj);
            this.addLabelToLayer(label, description, layer);
            this.addLayerToGeoms(layer, label, description);
            this.onSave({
                geoJsonAnnotation: () => {
                    this.geom.features = _.uniq(this.geom.features);
                    return this.geom;
                }});
        };
        return this.$compile(popupContent)(popupScope);
    }

    getLabelFromAutocomplete(label) {
        this.labelOriginalObject = label.originalObject;
        let theLabel = '';
        if (_.has(this.labelOriginalObject, 'name')) {
            theLabel = this.labelOriginalObject.name;
        } else {
            theLabel = this.labelOriginalObject;
        }
        if (!_.find(this.labelInputs, (eachInput) => eachInput.name === theLabel)) {
            this.labelInputs.push({'name': theLabel});
        }
        return theLabel;
    }

    addLabelToLayer(label, description, newLayer) {
        newLayer.bindPopup(
            `
            <label class="leaflet-popup-label">Label:<br/>
                <p>${label}</p></label><br/>
            <label class="leaflet-popup-label">Description:<br/>
                <p>${description}</p></label>
            `
        );
    }

    addLayerToGeoms(layer, label, description) {
        let newShape = layer.toGeoJSON();
        newShape.properties = {
            'label': label,
            'description': description,
            'id': new Date().getTime()
        };
        this.geom.features.push(newShape);
    }

    deleteLabelAndLayer(newLayer) {
        this.getMap().then((mapWrapper) => {
            let layers = mapWrapper.getLayers('Annotation');
            _.remove(layers, (lyr) => {
                return lyr === newLayer;
            });
            mapWrapper.setLayer('Annotation', layers, true);
        });
    }
}

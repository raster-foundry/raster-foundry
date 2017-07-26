/* globals L */

export default class AnnotateToolbarController {
    constructor(
        $log, $scope,
        mapService
    ) {
        'ngInject';
        this.$log = $log;
        this.$scope = $scope;

        this.getMap = () => mapService.getMap(this.mapId);
    }

    $onInit() {
        this.isDrawCancel = false;

        this.getMap().then((mapWrapper) => {
            this.listeners = [
                mapWrapper.on(L.Draw.Event.CREATED, this.createShape.bind(this))
            ];
            this.setDrawHandlers(mapWrapper);
        });

        this.$scope.$on('$destroy', this.$onDestroy.bind(this));
    }

    $onDestroy() {
        this.getMap().then((mapWrapper) => {
            this.listeners.forEach((listener) => {
                mapWrapper.off(listener);
            });
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
        this.isDrawCancel = true;
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
            this.drawPolygonHandler.disable();
            this.drawRectangleHandler.disable();
        }
        this.onShapeCreating({'isCreating': true});
    }

    onCancelDrawing() {
        this.isDrawCancel = false;
        this.drawRectangleHandler.disable();
        this.drawPolygonHandler.disable();
        this.drawMarkerHandler.disable();
        this.onShapeCreating({'isCreating': false});
    }

    createShape(e) {
        this.isDrawCancel = false;

        this.onShapeCreated({
            'shapeLayer': e.layer
        });
    }
}

/* globals L */
import angular from 'angular';
import annotateToolbarTpl from './annotateToolbar.html';
require('./annotateToolbar.scss');

import AnnotationActions from '_redux/actions/annotation-actions';

const AnnotateToolbarComponent = {
    templateUrl: annotateToolbarTpl,
    controller: 'AnnotateToolbarController',
    bindings: {
        mapId: '@',
        onShapeCreated: '&'
    }
};

class AnnotateToolbarController {
    constructor(
        $log, $scope, $ngRedux,
        mapService
    ) {
        'ngInject';
        this.$log = $log;
        this.$scope = $scope;

        let unsubscribe = $ngRedux.connect(
            this.mapStateToThis,
            AnnotationActions
        )(this);
        $scope.$on('$destroy', unsubscribe);

        this.getMap = () => mapService.getMap(this.mapId);
    }

    mapStateToThis(state) {
        return {
            editingAnnotation: state.projects.editingAnnotation,
            annotationTemplate: state.projects.annotationTemplate
        };
    }

    $onInit() {
        this.isDrawCancel = false;
        this.inBulkMode = false;
        this.lastHandler = null;

        this.getMap().then((mapWrapper) => {
            this.listeners = [
                mapWrapper.on(L.Draw.Event.CREATED, this.createShape.bind(this))
            ];
            this.setDrawHandlers(mapWrapper);
        });

        this.$scope.$on('$destroy', this.$onDestroy.bind(this));

        this.$scope.$watch('$ctrl.annotationTemplate', (template) => {
            if (template) {
                this.enableBulkCreate();
            } else {
                this.disableBulkCreate();
            }
        });
    }

    $onDestroy() {
        this.getMap().then((mapWrapper) => {
            this.listeners.forEach((listener) => {
                mapWrapper.off(listener);
            });
        });
        this.drawRectangleHandler.disable();
        this.drawPolygonHandler.disable();
        this.drawMarkerHandler.disable();
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
            this.lastHandler = this.drawRectangleHandler;
            this.drawPolygonHandler.disable();
            this.drawMarkerHandler.disable();
        } else if (shapeType === 'polygon') {
            this.drawPolygonHandler.enable();
            this.lastHandler = this.drawPolygonHandler;
            this.drawRectangleHandler.disable();
            this.drawMarkerHandler.disable();
        } else {
            this.drawMarkerHandler.enable();
            this.lastHandler = this.drawMarkerHandler;
            this.drawPolygonHandler.disable();
            this.drawRectangleHandler.disable();
        }
        this.disableSidebar();
    }

    enableBulkCreate() {
        this.inBulkMode = true;
    }

    disableBulkCreate() {
        this.inBulkMode = false;
        this.onCancelDrawing();
    }

    onCancelDrawing() {
        this.isDrawCancel = false;
        if (this.drawRectangleHandler) {
            this.drawRectangleHandler.disable();
        }
        if (this.drawPolygonHandler) {
            this.drawPolygonHandler.disable();
        }
        if (this.drawMarkerHandler) {
            this.drawMarkerHandler.disable();
        }
        this.enableSidebar();
    }

    createShape(e) {
        this.isDrawCancel = false;

        this.onShapeCreated({
            'shapeLayer': e.layer
        });

        if (this.inBulkMode && this.lastHandler) {
            this.$scope.$evalAsync(() => {
                this.isDrawCancel = true;
                this.lastHandler.enable();
                this.disableSidebar();
            });
        }
    }
}

const AnnotateToolbarModule = angular.module('components.map.annotateToolbar', []);

AnnotateToolbarModule.component('rfAnnotateToolbar', AnnotateToolbarComponent);
AnnotateToolbarModule.controller('AnnotateToolbarController', AnnotateToolbarController);

export default AnnotateToolbarModule;

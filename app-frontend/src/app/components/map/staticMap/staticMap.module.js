import angular from 'angular';
import Map from 'es6-map';
import staticMapTpl from './staticMap.html';

require('leaflet/dist/leaflet.css');

const StaticMapComponent = {
    templateUrl: staticMapTpl,
    controller: 'StaticMapController',
    bindings: {
        mapId: '@',
        options: '<?',
        initialCenter: '<?',
        initialZoom: '<?'
    }
};

class StaticMapController {
    constructor($rootScope, $log, $element, $scope, $timeout, mapService) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.isLoadingTiles = false;
        this.hasTileLoadingError = false;
        this.tileLoadingErrors = new Map();
        this.tileLoadingStatus = new Map();
        this.mapOptions = Object.assign({
            static: true,
            zoomControl: false,
            worldCopyJump: true,
            minZoom: 2,
            scrollWheelZoom: false,
            doubleClickZoom: false,
            dragging: false,
            touchZoom: false,
            boxZoom: false,
            keyboard: false,
            tap: false,
            maxZoom: 30
        }, this.options);
    }

    $postLink() {
        this.$timeout(() => {
            this.initMap();
        }, 0);
    }

    $onChanges(changes) {
        if (changes.options && changes.options.currentValue) {
            this.mapService.getMap(this.mapId).then((mapWrapper) => {
                mapWrapper.changeOptions(
                    Object.assign({}, this.mapOptions, changes.options.currentValue)
                );
            });
        }
    }

    $onDestroy() {
        this.mapService.deregisterMap(this.mapId);
    }

    initMap() {
        this.map = L.map(this.$element[0].children[0], this.mapOptions).setView(
            this.initialCenter ? this.initialCenter : [0, 0],
            this.initialZoom ? this.initialZoom : 2
        );
        this.mapWrapper = this.mapService.registerMap(this.map, this.mapId, this.mapOptions);
        this.addLoadingIndicator();
        this.map.invalidateSize();
    }

    addLoadingIndicator() {
        this.mapWrapper.onLayerGroupEvent('layeradd', this.handleLayerAdded, this);
    }

    handleLayerAdded(e) {
        let layer = e.layer;
        // eslint-disable-next-line no-underscore-dangle
        let isTileLayer = layer._tiles && layer._url;
        // eslint-disable-next-line no-underscore-dangle
        let layerId = layer._leaflet_id;

        if (isTileLayer) {
            this.setLoading(layerId, true);
            layer.on('loading', () => {
                this.setLoadingError(layerId, false);
                this.setLoading(layerId, true);
            });
            layer.on('load', () => {
                this.setLoading(layerId, false);
                this.map.invalidateSize();
            });
            layer.on('tileerror', () => {
                this.setLoadingError(layerId, true);
            });
        }
    }

    setLoading(layerId, status) {
        this.tileLoadingStatus.set(layerId, status);
        this.$scope.$evalAsync(() => {
            this.isLoadingTiles =
                Array.from(this.tileLoadingStatus.values()).reduce((acc, cur) => acc || cur, false);
        });
    }

    setLoadingError(layerId, status) {
        this.tileLoadingErrors.set(layerId, status);
        this.$scope.$evalAsync(() => {
            this.hasTileLoadingError =
                Array.from(this.tileLoadingErrors.values()).reduce((acc, cur) => acc || cur, false);
        });
    }
}

const StaticMapModule = angular.module('components.map.staticMap', []);

StaticMapModule.component('rfStaticMap', StaticMapComponent);
StaticMapModule.controller('StaticMapController', StaticMapController);

export default StaticMapModule;

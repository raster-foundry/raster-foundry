import Map from 'es6-map';

export default class StaticMapController {
    constructor($log, $element, $scope, $timeout, mapService) {
        'ngInject';
        this.$element = $element;
        this.$log = $log;
        this.$scope = $scope;
        this.$timeout = $timeout;
        this.mapService = mapService;
    }

    $onInit() {
        this.isLoadingTiles = false;
        this.hasTileLoadingError = false;
        this.tileLoadingErrors = new Map();
        this.tileLoadingStatus = new Map();
        this.initMap();
    }

    $onChanges(changes) {
        if (changes.options && changes.options.currentValue) {
            this.mapService.getMap(this.mapId).then((mapWrapper) => {
                mapWrapper.changeOptions(changes.options.currentValue);
            });
        }
    }

    $onDestroy() {
        this.mapService.deregisterMap(this.mapId);
    }

    initMap() {
        this.map = L.map(this.$element[0].children[0], {
            zoomControl: false,
            worldCopyJump: true,
            minZoom: 2,
            scrollWheelZoom: false,
            doubleClickZoom: false,
            dragging: false,
            touchZoom: false,
            boxZoom: false,
            keyboard: false,
            tap: false
        }).setView(
            this.initialCenter ? this.initialCenter : [0, 0],
            this.initialZoom ? this.initialZoom : 2
        );


        this.$scope.$evalAsync(() => {
            this.mapWrapper = this.mapService.registerMap(this.map, this.mapId, { static: true });
            this.addLoadingIndicator();
        });

        this.$timeout(() => {
            this.map.invalidateSize();
        });
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

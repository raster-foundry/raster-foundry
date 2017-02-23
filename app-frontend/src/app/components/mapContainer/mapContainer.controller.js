import Map from 'es6-map';

export default class MapContainerController {
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
        this.options = this.options ? this.options : {};
        this.map = L.map(this.$element[0].children[0], {
            zoomControl: false,
            worldCopyJump: true,
            minZoom: 2,
            scrollWheelZoom: !this.options.static,
            doubleClickZoom: !this.options.static,
            dragging: !this.options.static,
            touchZoom: !this.options.static,
            boxZoom: !this.options.static,
            keyboard: !this.options.static,
            tap: !this.options.static
        }).setView(
            this.initialCenter ? this.initialCenter : [0, 0],
            this.initialZoom ? this.initialZoom : 2
        );


        this.$timeout(() => {
            this.map.invalidateSize();
            this.mapWrapper = this.mapService.registerMap(this.map, this.mapId, this.options);
            this.addLoadingIndicator();
        }, 400);
    }

    addLoadingIndicator() {
        this.mapWrapper.onLayerGroupEvent('layeradd', this.handleLayerAdded, this);
    }

    handleLayerAdded(e) {
        let layer = e.layer;
        let isTileLayer = layer._tiles && layer._url;
        let layerId = layer._leaflet_id;

        if (isTileLayer) {
            this.setLoading(layerId, true);
            layer.on('loading', () => {
                this.setLoadingError(layerId, false);
                this.setLoading(layerId, true);
            });
            layer.on('load', () => {
                this.setLoading(layerId, false);
            });
            layer.on('tileerror', () => {
                // @TODO: when tiles outside the extent of projects are
                // properly returned, we should uncomment the following line
                // this.setLoadingError(layerId, true);
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

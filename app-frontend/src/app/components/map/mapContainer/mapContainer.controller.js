import Map from 'es6-map';
/* globals BUILDCONFIG */

export default class MapContainerController {
    constructor($document, $element, $scope, $timeout, $uibModal, mapService) {
        'ngInject';
        this.$document = $document;
        this.$element = $element;
        this.$scope = $scope;
        this.$timeout = $timeout;
        this.$uibModal = $uibModal;
        this.mapService = mapService;
        this.getMap = () => this.mapService.getMap(this.mapId);
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
            this.getMap().then((mapWrapper) => {
                mapWrapper.changeOptions(changes.options.currentValue);
            });
        }
    }

    $onDestroy() {
        this.mapService.deregisterMap(this.mapId);
        delete this.mapWrapper;
        if (this.clickListener) {
            this.$document.off('click', this.clickListener);
        }
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
        ).on('zoom', () => {
            this.zoomLevel = this.map.getZoom();
            this.$scope.$evalAsync();
        });


        this.$timeout(() => {
            this.map.invalidateSize();
            this.mapWrapper = this.mapService.registerMap(this.map, this.mapId, this.options);
            this.addLoadingIndicator();
        }, 400);

        this.basemapOptions = BUILDCONFIG.BASEMAPS.layers;
        this.basemapKeys = Object.keys(this.basemapOptions);
    }

    zoomIn() {
        this.map.zoomIn();
        this.$timeout(() => {}, 500);
        this.zoomLevel = this.map.getZoom();
    }

    zoomOut() {
        this.map.zoomOut();
        this.$timeout(()=> {}, 500);
        this.zoomLevel = this.map.getZoom();
    }

    toggleFullscreen() {
        // fullscreen mode is only supported in vendor-specific mode right now
        let mapElement = this.$element[0];

        if (this.$document[0].webkitFullscreenEnabled && mapElement.webkitRequestFullscreen) {
            if (!this.inFullscreenMode) {
                mapElement.webkitRequestFullscreen();
                this.inFullscreenMode = true;
            } else {
                this.$document[0].webkitExitFullscreen();
                this.inFullscreenMode = false;
            }
        } else if (this.$document[0].mozFullScreenEnabled && mapElement.mozRequestFullScreen) {
            if (!this.inFullscreenMode) {
                mapElement.mozRequestFullScreen();
                this.inFullscreenMode = true;
            } else {
                this.$document[0].mozCancelFullScreen();
                this.inFullscreenMode = false;
            }
        } else if (this.$document[0].msFullscreenEnabled && mapElement.msRequestFullscreen) {
            if (!this.inFullscreenMode) {
                mapElement.msRequestFullscreen();
                this.inFullscreenMode = true;
            } else {
                this.$document[0].msExitFullscreen();
                this.inFullscreenMode = false;
            }
        }
    }

    toggleLayerPicker(event) {
        event.stopPropagation();
        const onClick = () => {
            this.layerPickerOpen = false;
            this.$document.off('click', this.clickListener);
            this.$scope.$evalAsync();
        };
        if (!this.layerPickerOpen) {
            this.layerPickerOpen = true;
            this.clickListener = onClick;
            this.$document.on('click', onClick);
        } else {
            this.layerPickerOpen = false;
            this.$document.off('click', this.clickListener);
            delete this.clickListener;
        }
    }

    toggleableLayers() {
        if (this.mapWrapper) {
            // eslint-disable-next-line
            return Array.from(this.mapWrapper._toggleableLayers.values());
        }
        return [];
    }


    layerEnabled(layerId) {
        if (this.mapWrapper) {
            return this.mapWrapper.getLayerVisibility(layerId) === 'visible';
        }
        return false;
    }

    toggleLayer(layerId) {
        const layerState = this.mapWrapper.getLayerVisibility(layerId);
        if (layerState === 'visible') {
            this.mapWrapper.hideLayers(layerId);
        } else if (layerState === 'hidden' || layerState === 'mixed') {
            this.mapWrapper.showLayers(layerId);
        }
    }

    toggleGeojson() {
        if (this.mapWrapper.getGeojsonVisibility() === 'hidden') {
            this.mapWrapper.showGeojson();
        } else {
            this.mapWrapper.hideGeojson();
        }
    }

    geojsonEnabled() {
        if (this.mapWrapper) {
            return this.mapWrapper.getGeojsonVisibility() === 'visible';
        }
        return false;
    }

    mapHasGeojson() {
        if (this.mapWrapper) {
            // eslint-disable-next-line
            return this.mapWrapper._geoJsonMap.size > 0;
        }
        return false;
    }

    setBasemap(basemapKey) {
        this.mapWrapper.setBasemap(basemapKey);
    }

    getBasemapStyle(basemapKey) {
        if (this.mapWrapper) {
            let options = this.basemapOptions[basemapKey];
            let url = L.Util.template(
                options.url,
                Object.assign(
                    {
                        s: options.properties.subdomains && options.properties.subdomains[0] ?
                            options.properties.subdomains[0] : 'a',
                        z: '4',
                        x: '8',
                        y: '6'
                    },
                    options.properties
                )
            );
            return {'background': `url(${url}) no-repeat center`};
        }
        return {};
    }

    cancelPropagation(event) {
        event.stopPropagation();
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

    openMapSearchModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfMapSearchModal',
            resolve: { }
        });

        this.activeModal.result.then(
            location => {
                const mapView = location.mapView;
                this.map.fitBounds([
                    [mapView.bottomRight.latitude, mapView.bottomRight.longitude],
                    [mapView.topLeft.latitude, mapView.topLeft.longitude]
                ]);
            });
    }
}

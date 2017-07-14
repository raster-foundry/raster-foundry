// import Map from 'es6-map';
/* globals BUILDCONFIG */

export default class LabMapController {
    constructor(
        $log, $document, $element, $scope, $timeout,
        mapService
    ) {
        'ngInject';
        this.$log = $log;
        this.$document = $document;
        this.$element = $element;
        this.$scope = $scope;
        this.$timeout = $timeout;
        this.mapService = mapService;
        this.getMap = () => this.mapService.getMap(this.mapId);
    }

    $onInit() {
        this.initMap();
    }

    $onChanges(changes) {
        if (changes.options && changes.options.currentValue) {
            this.getMap().then((mapWrapper) => {
                mapWrapper.changeOptions(changes.options.currentValues);
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
        );


        this.$timeout(() => {
            this.map.invalidateSize();
            this.mapWrapper = this.mapService.registerMap(this.map, this.mapId, this.options);
        }, 400);

        this.basemapOptions = BUILDCONFIG.BASEMAPS.layers;
        this.basemapKeys = Object.keys(this.basemapOptions);
    }

    zoomIn() {
        this.map.zoomIn();
        this.$timeout(() => {}, 500);
    }

    zoomOut() {
        this.map.zoomOut();
        this.$timeout(()=> {}, 500);
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
}

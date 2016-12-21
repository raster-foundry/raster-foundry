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
        }).setView([0, 0], 2);


        let cartoPositron = L.tileLayer(
            'https://cartodb-basemaps-{s}.global.ssl.fastly.net/light_all/{z}/{x}/{y}.png', {
                attribution: '&copy; <a href="http://www.openstreetmap.org/copyright">' +
                    'OpenStreetMap</a> &copy;<a href="http://cartodb.com/attributions">CartoDB</a>',
                maxZoom: 19
            }
        );
        cartoPositron.addTo(this.map);

        this.$timeout(() => {
            this.map.invalidateSize();
            this.mapService.registerMap(this.map, this.mapId, this.options);
        }, 400);
    }
}

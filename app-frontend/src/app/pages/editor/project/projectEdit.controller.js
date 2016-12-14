const Map = require('es6-map');

export default class ProjectEditController {
    constructor($scope // eslint-disable-line max-params
    ) {
        'ngInject';
        this.selectedScenes = new Map();
        this.selectedLayers = new Map();
        this.sceneList = [];
        this.sceneLayers = new Map();
        this.layers = [];
        this.highlight = null;
        this.$scope = $scope;
        // FIXME: Right now initialization doesn't work very well; this is just a hack that assumes
        // we know what the map zoom initial zoom will be, which is fragile. This should be
        // replaced when we refactor the map.
        this.mapZoom = 2;
    }

    onMapClick(event) {
        this.clickEvent = event;
        this.$scope.$apply();
    }

    onViewChange(newBounds, zoom) {
        this.mapZoom = zoom;
        this.$scope.$apply();
    }

    setHoveredScene(scene) {
        this.hoveredScene = scene;
    }

    removeHoveredScene() {
        this.hoveredScene = null;
    }

    setHighlight(highlight) {
        this.highlight = highlight;
    }
}

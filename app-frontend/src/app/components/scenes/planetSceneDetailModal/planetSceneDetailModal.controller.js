/* global L */

export default class PlanetSceneDetailModalController {
    constructor(
        $rootScope, $scope, mapService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }
    $onInit() {
        this.$scope.$on('$destroy', () => {
            this.mapService.deregisterMap('scene-preview-map');
        });
        this.scene = this.resolve.scene;
        this.planetThumbnailUrl = this.resolve.planetThumbnailUrl;
    }

    $postLink() {
        this.getMap().then(mapWrapper => {
            // the order of the below two function calls is important
            mapWrapper.map.fitBounds(this.getSceneBounds());
            mapWrapper.setThumbnail(this.scene, {
                persist: true,
                dataRepo: 'Planet Labs',
                url: this.planetThumbnailUrl
            });
        });
    }

    getMap() {
        return this.mapService.getMap('scene-preview-map');
    }

    getSceneBounds() {
        return L.geoJSON(this.scene.tileFootprint).getBounds();
    }
}

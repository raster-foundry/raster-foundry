/* global L */

export default class PlanetSceneDetailModalController {
    constructor(
        $scope, mapService
    ) {
        'ngInject';
        this.scene = this.resolve.scene;
        this.planetThumbnailUrl = this.resolve.planetThumbnailUrl;
        this.getMap = () => mapService.getMap('scene-preview-map');
        $scope.$on('$destroy', () => {
            mapService.deregisterMap('scene-preview-map');
        });
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

    getSceneBounds() {
        return L.geoJSON(this.scene.tileFootprint).getBounds();
    }
}

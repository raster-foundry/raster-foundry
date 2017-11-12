/* global L */

export default class PlanetSceneDetailModalController {
    constructor(
        $log, $state, $uibModal,
        moment, sceneService, datasourceService, mapService
    ) {
        'ngInject';
        this.$log = $log;
        this.$state = $state;
        this.$uibModal = $uibModal;
        this.Moment = moment;
        this.sceneService = sceneService;
        this.datasourceService = datasourceService;
        this.scene = this.resolve.scene;
        this.planetKey = this.resolve.planetKey;
        this.getMap = () => mapService.getMap('scene-preview-map');
    }

    $onInit() {
        this.getMap().then(mapWrapper => {
            mapWrapper.setPlanetThumbnail(this.scene, true, this.planetKey);
            mapWrapper.map.fitBounds(this.getSceneBounds());
        });
    }

    getSceneBounds() {
        const bounds = L.geoJSON(this.scene.dataFootprint).getBounds();
        return bounds;
    }

    closeWithData(data) {
        this.close({$value: data});
    }

    openDownloadModal() {
       // Do we implement planet scene download?
    }

}

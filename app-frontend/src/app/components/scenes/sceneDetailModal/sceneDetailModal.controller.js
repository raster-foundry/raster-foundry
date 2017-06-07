/* global L */

export default class SceneDetailModalController {
    constructor(
        $state, sceneService, datasourceService, mapService, $uibModal
    ) {
        'ngInject';
        this.$state = $state;
        this.sceneService = sceneService;
        this.datasourceService = datasourceService;
        this.$uibModal = $uibModal;
        this.scene = this.resolve.scene;
        this.getMap = () => mapService.getMap('scene-preview-map');
    }

    $onInit() {
        this.datasourceLoaded = false;
        this.getMap().then(mapWrapper => {
            mapWrapper.setThumbnail(this.scene, false, true);
            mapWrapper.map.fitBounds(this.getSceneBounds());
        });
        this.datasourceService.get(this.scene.datasource).then(d => {
            this.datasourceLoaded = true;
            this.datasource = d;
        });
    }

    openDownloadModal() {
        const images = this.scene.images.map(i => Object({
            filename: i.filename,
            uri: i.sourceUri,
            metadata: i.metadataFiles || []
        }));

        const downloadSets = [{
            label: this.scene.name,
            metadata: this.scene.metadataFiles || [],
            images: images
        }];

        this.activeModal = this.$uibModal.open({
            component: 'rfSceneDownloadModal',
            resolve: {
                downloads: () => downloadSets
            }
        });

        this.dismiss();

        return this.activeModal;
    }

    getSceneBounds() {
        const bounds = L.geoJSON(this.scene.dataFootprint).getBounds();
        return bounds;
    }

    closeWithData(data) {
        this.close({$value: data});
    }
}

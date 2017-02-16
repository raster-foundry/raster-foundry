const Map = require('es6-map');

class SceneDetailController {
    constructor($log, $state, sceneService, $uibModal, mapService) {
        'ngInject';

        this.$state = $state;
        this.$uibModal = $uibModal;
        this.$log = $log;
        this.getMap = () => mapService.getMap('scene');

        this.mapOptions = {
            static: true,
            fitToGeojson: true
        };

        this.scene = this.$state.params.scene;
        this.sceneId = this.$state.params.id;
        if (!this.scene) {
            if (this.sceneId) {
                this.loading = true;
                sceneService.query({id: this.sceneId}).then(
                    (scene) => {
                        this.scene = scene;
                        this.loading = false;
                        this.addThumbnailToMap();
                    },
                    () => {
                        this.$state.go('^.list');
                    }
                );
            } else {
                this.$state.go('^.list');
            }
        } else {
            this.addThumbnailToMap();
        }
    }

    addThumbnailToMap() {
        this.getMap().then(
            (map)=> {
                map.setThumbnail(this.scene, true);
            }
        );
    }

    downloadModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        let images = this.scene.images.map((image) => {
            return {
                filename: image.filename,
                uri: image.sourceUri,
                metadata: image.metadataFiles || []
            };
        });

        let downloadSets = [{
            label: this.scene.name,
            metadata: this.scene.metadataFiles || [],
            images: images
        }];

        this.activeModal = this.$uibModal.open({
            component: 'rfDownloadModal',
            resolve: {
                downloads: () => downloadSets
            }
        });
    }

    projectModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }
        let sceneMap = new Map();
        sceneMap.set(this.scene.id, this.scene);

        this.activeModal = this.$uibModal.open({
            component: 'rfProjectAddModal',
            resolve: {
                scenes: () => sceneMap
            }
        });

        this.activeModal.result.then(() => {
            delete this.activeModal;
        }, () => {
            delete this.activeModal;
        });
    }
}
export default SceneDetailController;

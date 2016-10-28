class SceneDetailController {
    constructor($log, $state, sceneService, $uibModal) {
        'ngInject';

        this.$state = $state;
        this.$uibModal = $uibModal;
        this.$log = $log;

        this.scene = this.$state.params.scene;
        this.sceneId = this.$state.params.id;
        if (!this.scene) {
            if (this.sceneId) {
                this.loading = true;
                sceneService.query({id: this.sceneId}).then(
                    (scene) => {
                        this.scene = scene;
                        this.loading = false;
                    },
                    () => {
                        this.$state.go('^.list');
                    }
                );
            } else {
                this.$state.go('^.list');
            }
        }
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
}
export default SceneDetailController;

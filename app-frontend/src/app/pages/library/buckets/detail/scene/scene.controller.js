class BucketSceneController {
    constructor($log, $state, sceneService, bucketService, $uibModal) {
        'ngInject';
        this.$log = $log;
        this.$state = $state;
        this.bucketService = bucketService;
        this.sceneService = sceneService;
        this.$uibModal = $uibModal;

        this.scene = this.$state.params.scene;
        this.bucketId = this.$state.params.bucketid;
        this.sceneId = this.$state.params.sceneid;

        if (!this.scene) {
            if (this.sceneId) {
                this.loading = true;
                sceneService.query({id: this.sceneId}).then(
                    (scene) => {
                        this.scene = scene;
                        this.loading = false;
                    },
                    () => {
                        this.$state.go('^.scenes');
                    }
                );
            } else {
                this.$state.go('^.scenes');
            }
        }

        $log.debug('BucketSceneController initialized');
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

    removeScene() {
        this.bucketService.removeSceneFromBucket({
            bucketId: this.bucketId,
            sceneId: this.sceneId
        }).then(
            () => {
                this.$state.go('^.scenes');
            },
            (err) => {
                this.$log.error('Unable to remove scene from bucket:', err);
            });
    }
}

export default BucketSceneController;

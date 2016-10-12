class BucketSceneController {
    constructor($log, auth, $state, sceneService, bucketService) {
        'ngInject';
        this.$log = $log;
        this.auth = auth;
        this.$state = $state;
        this.bucketService = bucketService;
        this.sceneService = sceneService;

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

export default class BucketScenesController {
    constructor( // eslint-disable-line max-params
        $log, $state, bucketService, $scope, $uibModal
    ) {
        'ngInject';

        this.$log = $log;
        this.$state = $state;
        this.bucketService = bucketService;
        this.$parent = $scope.$parent.$ctrl;
        this.$uibModal = $uibModal;

        this.bucket = this.$state.params.bucket;
        this.bucketId = this.$state.params.bucketid;

        if (!this.bucket) {
            if (this.bucketId) {
                this.loading = true;
                this.bucketService.query({id: this.bucketId}).then(
                    (bucket) => {
                        this.bucket = bucket;
                        this.loading = false;
                        this.populateSceneList($state.params.page || 1);
                    },
                    () => {
                        this.$state.go('^.^.list');
                    }
                );
            } else {
                this.$state.go('^.^.list');
            }
        } else {
            this.populateSceneList($state.params.page || 1);
        }
    }

    populateSceneList(page) {
        if (this.loading) {
            return;
        }
        delete this.errorMsg;
        this.loading = true;
        // save off selected scenes so you don't lose them during the refresh
        this.sceneList = [];
        this.bucketService.getBucketScenes(
            {
                sort: 'createdAt,desc',
                pageSize: '10',
                page: page - 1,
                bucketId: this.bucket.id
            }
        ).then((sceneResult) => {
            this.lastSceneResult = sceneResult;
            this.numPaginationButtons = 6 - sceneResult.page % 10;
            if (this.numPaginationButtons < 3) {
                this.numPaginationButtons = 3;
            }
            this.currentPage = sceneResult.page + 1;
            let replace = !this.$state.params.page;
            this.$state.transitionTo(
                this.$state.$current.name,
                {
                    bucketid: this.bucket.id, page: this.currentPage
                },
                {
                    location: replace ? 'replace' : true,
                    notify: false
                }
            );
            this.sceneList = this.lastSceneResult.results;
            this.loading = false;
        }, () => {
            this.errorMsg = 'Server error.';
            this.loading = false;
        });
    }

    viewSceneDetail(scene) {
        this.$state.go(
            '^.scene',
            {
                scene: scene,
                sceneid: scene.id
            }
        );
    }

    selectNone() {
        this.$parent.selectedScenes.clear();
    }

    isSelected(scene) {
        return this.$parent.selectedScenes.has(scene.id);
    }

    setSelected(scene, selected) {
        if (selected) {
            this.$parent.selectedScenes.set(scene.id, scene);
        } else {
            this.$parent.selectedScenes.delete(scene.id);
        }
    }

    deleteBucket() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }
        this.activeModal = this.$uibModal.open({
            component: 'rfConfirmationModal',
            resolve: {
                title: () => 'Delete bucket?',
                content: () =>
                    'The bucket will be permanently deleted,'
                    + 'but scenes will be unaffected.',
                confirmText: () => 'Delete Bucket',
                cancelText: () => 'Cancel'
            }
        });
        this.activeModal.result.then(
            () => {
                this.bucketService.deleteBucket(this.bucketId).then(
                    () => {
                        this.$state.go('^.^.list');
                    },
                    (err) => {
                        this.$log.debug('error deleting bucket', err);
                    }
                );
            });
    }

    shouldShowPagination() {
        return !this.loading &&
            this.lastSceneResult &&
            this.lastSceneResult.count !== 0 &&
            !this.errorMsg;
    }

    shouldShowScenePlaceholder() {
        return !this.loading &&
            this.lastSceneResult &&
            this.lastSceneResult.count === 0;
    }

    removeScenes() {
        let sceneIds = Array.from(this.$parent.selectedScenes.keys());
        this.bucketService.removeScenesFromBucket(this.bucketId, sceneIds).then(
            () => {
                this.populateSceneList(this.currentPage);
                this.$parent.selectedScenes.clear();
            },
            (err) => {
                // later on, use toasts or something instead of a debug message
                this.$log.debug('Error removing scenes from bucket.', err);
                this.populateSceneList(this.currentPage);
            }
        );
    }
}

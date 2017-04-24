export default class SceneListController {
    constructor( // eslint-disable-line max-params
        $log, sceneService, $state, authService, $uibModal
    ) {
        'ngInject';
        this.$log = $log;
        this.sceneService = sceneService;
        this.$state = $state;
        this.authService = authService;
        this.$uibModal = $uibModal;
    }

    $onInit() {
        this.populateSceneList(this.$state.params.page || 1);
    }

    populateSceneList(page) {
        if (this.loading) {
            return;
        }
        delete this.errorMsg;
        this.loading = true;
        // save off selected scenes so you don't lose them during the refresh
        this.sceneList = [];
        this.sceneService.query(
            {
                sort: 'createdAt,desc',
                pageSize: '10',
                page: page - 1,
                owner: this.authService.profile().user_id
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
                {page: this.currentPage},
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
            'library.scenes.detail',
            {
                scene: scene,
                id: scene.id
            }
        );
    }

    importModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfImportModal',
            resolve: {}
        });
    }

    downloadModal(scene) {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        let images = scene.images.map((image) => {
            return {
                filename: image.filename,
                uri: image.sourceUri,
                metadata: image.metadataFiles || []
            };
        });

        let downloadSets = [{
            label: scene.name,
            metadata: scene.metadataFiles || [],
            images: images
        }];

        this.activeModal = this.$uibModal.open({
            component: 'rfDownloadModal',
            resolve: {
                downloads: () => downloadSets
            }
        });
    }

    shouldShowSceneList() {
        return !this.loading && this.lastSceneResult &&
            this.lastSceneResult.count > this.lastSceneResult.pageSize && !this.errorMsg;
    }

    shouldShowImportBox() {
        return !this.loading && this.lastSceneResult &&
            this.lastSceneResult.count === 0 && !this.errorMsg;
    }
}

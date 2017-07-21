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
        this.sceneActions = [
            {
                label: 'View',
                onClick: this.viewSceneDetail.bind(this)
            },
            {
                label: 'Download',
                onClick: this.downloadModal.bind(this),
                iconClass: 'icon-download'
            },
            // {
            //     label: 'Make Public',
            //     iconClass: 'icon-unlocked'
            // },
            {
                divider: true
            },
            {
                label: 'Delete',
                textClass: 'dropdown-danger',
                iconClass: 'icon-trash',
                onClick: this.deleteModal.bind(this)
            }
        ];
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
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfSceneDetailModal',
            resolve: {
                scene: () => scene
            }
        });
    }

    importModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfSceneImportModal',
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
            component: 'rfSceneDownloadModal',
            resolve: {
                downloads: () => downloadSets
            }
        });
    }

    deleteModal(scene) {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfConfirmationModal',
            resolve: {
                title: () => 'Delete Imported Scene?',
                subtitle: () =>
                    'The scene will be permanently deleted,'
                    + ' but any projects containing it will remain.',
                content: () =>
                    '<div class="text-center color-danger">'
                    + 'You are about to delete the scene. This action is not reversible.'
                    + ' Are you sure you wish to continue?'
                    + '</div>',
                confirmText: () => 'Delete Scene',
                cancelText: () => 'Cancel'
            }
        });

        this.activeModal.result.then(
            () => {
                this.sceneService.deleteScene(scene).then(
                    () => {
                        this.$state.reload();
                    },
                    (err) => {
                        this.$log.debug('error deleting scene', err);
                    }
                );
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

    onActionClick(action, scene) {
        if (action.onClick) {
            action.onClick(scene);
        }
    }

    getActionIcon(action) {
        let classes = {};
        if (action.iconClass) {
            classes[action.iconClass] = true;
        }
        return classes;
    }

    getActionTextClass(action) {
        let classes = {};
        if (action.textClass) {
            classes[action.textClass] = true;
        }
        return classes;
    }
}

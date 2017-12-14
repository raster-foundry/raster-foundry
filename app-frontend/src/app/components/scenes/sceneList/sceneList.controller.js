export default class SceneListController {
    constructor( // eslint-disable-line max-params
        $log, sceneService, $state, authService, modalService
    ) {
        'ngInject';
        this.$log = $log;
        this.sceneService = sceneService;
        this.$state = $state;
        this.authService = authService;
        this.modalService = modalService;
    }

    $onInit() {
        this.populateSceneList(this.$state.params.page || 1);
        this.sceneActions = [
            {
                label: 'Download',
                onClick: this.downloadModal.bind(this),
                iconClass: 'icon-download'
            },
            {
                label: 'Delete',
                buttonClass: 'btn-danger',
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
                owner: this.authService.getProfile().sub
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
        this.modalService.open({
            component: 'rfSceneDetailModal',
            resolve: {
                scene: () => scene
            }
        });
    }

    importModal() {
        this.modalService.open({
            component: 'rfSceneImportModal',
            resolve: {}
        });
    }

    downloadModal(scene) {
        this.sceneService.getDownloadableImages(scene).then(images => {
            const imageSet = images.map(image => {
                return {
                    filename: image.filename,
                    uri: image.downloadUri,
                    metadata: image.metadataFiles || []
                };
            });

            let downloadSets = [{
                label: scene.name,
                metadata: scene.metadataFiles || [],
                images: imageSet
            }];

            this.modalService.open({
                component: 'rfSceneDownloadModal',
                resolve: {
                    downloads: () => downloadSets
                }
            });
        });
    }

    deleteModal(scene) {
        const modal = this.modalService.open({
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

        modal.result.then(() => {
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

    onActionClick(event, action, scene) {
        event.stopPropagation();
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

    getActionButtonClass(action) {
        let classes = {};
        if (action.buttonClass) {
            classes[action.buttonClass] = true;
        }
        return classes;
    }
}

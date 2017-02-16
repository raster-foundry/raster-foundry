class ScenesListController {
    constructor( // eslint-disable-line max-params
        $log, sceneService, $state, $scope, $uibModal, authService
    ) {
        'ngInject';

        this.$log = $log;
        this.sceneService = sceneService;
        this.$state = $state;
        this.$scope = $scope;
        this.$parent = $scope.$parent.$ctrl;
        this.$uibModal = $uibModal;
        this.authService = authService;

        this.sceneList = [];
        this.populateSceneList($state.params.page || 1);
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
                createdBy: this.authService.profile().user_id
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

    selectAll() {
        this.sceneList.forEach((scene) => {
            this.setSelected(scene, true);
        });
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

    projectModal() {
        if (!this.$parent.selectedScenes || this.$parent.selectedScenes.size === 0) {
            return;
        }

        if (this.activeModal) {
            this.activeModal.dismiss();
        }
        this.activeModal = this.$uibModal.open({
            component: 'rfProjectAddModal',
            resolve: {
                scenes: () => this.$parent.selectedScenes
            }
        });

        this.activeModal.result.then((result) => {
            if (result && result === 'scenes') {
                this.sceneModal();
            }
            delete this.activeModal;
        }, () => {
            delete this.activeModal;
        });
    }

    sceneModal() {
        if (!this.$parent.selectedScenes || this.$parent.selectedScenes.size === 0) {
            return;
        }

        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfSelectedScenesModal',
            resolve: {
                scenes: () => this.$parent.selectedScenes
            }
        });

        this.activeModal.result.then((result) => {
            if (result === 'project') {
                this.projectModal();
            } else {
                this.$log.debug('modal result: ', result, ' is not implemented yet');
            }
            delete this.activeModal;
        }, () => {
            delete this.activeModal;
        });
    }
}

export default ScenesListController;

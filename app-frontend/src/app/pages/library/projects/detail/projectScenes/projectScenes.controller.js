export default class ProjectScenesController {
    constructor( // eslint-disable-line max-params
        $log, $state, $location, projectService, $scope, $uibModal
    ) {
        'ngInject';

        this.$log = $log;
        this.$state = $state;
        this.$location = $location;
        this.projectService = projectService;
        this.$parent = $scope.$parent.$ctrl;
        this.$uibModal = $uibModal;
        this.$scope = $scope;
    }

    $onInit() {
        this.project = this.$state.params.project;
        this.projectId = this.$state.params.projectid;

        this.isEditingProjectName = false;
        this.isSavingProjectNameEdit = false;
        this.showError = false;

        if (!this.project) {
            if (this.projectId) {
                this.loading = true;
                this.projectService.query({id: this.projectId}).then(
                    (project) => {
                        this.project = project;
                        this.loading = false;
                        this.populateSceneList(this.$state.params.page || 1);
                    },
                    () => {
                        this.$state.go('^.^.list');
                    }
                );
            } else {
                this.$state.go('^.^.list');
            }
        } else {
            this.populateSceneList(this.$state.params.page || 1);
        }

        this.$scope.$on('$destroy', () => {
            if (this.activeModal) {
                this.activeModal.dismiss();
            }
        });
    }

    populateSceneList(page) {
        if (this.loading) {
            return;
        }
        delete this.errorMsg;
        this.loading = true;
        // save off selected scenes so you don't lose them during the refresh
        this.sceneList = [];
        this.projectService.getProjectScenes(
            {
                sort: 'createdAt,desc',
                pageSize: '10',
                page: page - 1,
                projectId: this.project.id
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
                    projectid: this.project.id, page: this.currentPage
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

    deleteProject() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }
        this.activeModal = this.$uibModal.open({
            component: 'rfConfirmationModal',
            resolve: {
                title: () => 'Delete Project?',
                subtitle: () =>
                    'The project will be permanently deleted,'
                    + ' but scenes will be unaffected.',
                content: () =>
                    '<div class="text-center color-danger">'
                    + 'You are about to delete the project. This action is not reversible.'
                    + ' Are you sure you wish to continue?'
                    + '</div>',
                confirmText: () => 'Delete Project',
                cancelText: () => 'Cancel'
            }
        });
        this.activeModal.result.then(
            () => {
                this.projectService.deleteProject(this.projectId).then(
                    () => {
                        this.$state.go('^.^.list');
                    },
                    (err) => {
                        this.$log.debug('error deleting project', err);
                    }
                );
            });
    }

    downloadModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }
        let downloadSets = Array.from(this.$parent.selectedScenes)
            .map(([, val]) => {
                let images = val.images.map((image) => {
                    return {
                        filename: image.filename,
                        uri: image.sourceUri,
                        metadata: image.metadataFiles || []
                    };
                });

                return {
                    label: val.name,
                    images: images,
                    metadata: val.metadataFiles || []
                };
            });
        if (downloadSets.length > 0) {
            this.activeModal = this.$uibModal.open({
                component: 'rfDownloadModal',
                resolve: {
                    downloads: () => downloadSets
                }
            });
        }
    }

    publishModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfPublishModal',
            resolve: {
                project: () => this.project,
                tileUrl: () => this.projectService.getProjectLayerURL(this.project),
                shareUrl: () => this.projectService.getProjectShareURL(this.project)
            }
        });

        return this.activeModal;
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
        this.projectService.removeScenesFromProject(this.projectId, sceneIds).then(
            () => {
                this.populateSceneList(this.currentPage);
                this.$parent.selectedScenes.clear();
            },
            (err) => {
                // later on, use toasts or something instead of a debug message
                this.$log.debug('Error removing scenes from project.', err);
                this.populateSceneList(this.currentPage);
            }
        );
    }

    toggleProjectNameEdit() {
        if (this.isEditingProjectName) {
            this.cancelProjectNameEdit();
        } else {
            this.startProjectNameEdit();
        }
    }

    startProjectNameEdit() {
        this.editedProjectName = this.project.name;
        this.isEditingProjectName = true;
    }

    saveProjectNameEdit() {
        const cachedProjectName = this.project.name;
        this.isSavingProjectNameEdit = true;
        this.project.name = this.editedProjectName;
        this.isEditingProjectName = false;
        this.projectService.updateProject(this.project).then(
            () => {
                this.isSavingProjectNameEdit = false;
            },
            (err) => {
                this.showError = true;
                this.$log.error('Error renaming project:', err);
                this.project.name = cachedProjectName;
                this.isSavingProjectNameEdit = false;
            }
        );
    }

    cancelProjectNameEdit() {
        this.isEditingProjectName = false;
        this.editedProjectName = this.project.name;
    }

    dismissErrorMessage() {
        this.showError = false;
    }
 }

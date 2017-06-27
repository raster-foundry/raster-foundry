export default class ProjectsDetailController {
    constructor( // eslint-disable-line max-params
        $log, $state, $location, projectService, $scope, $uibModal,
        mapService, authService, mapUtilsService
    ) {
        'ngInject';

        this.$log = $log;
        this.$state = $state;
        this.$location = $location;
        this.projectService = projectService;
        this.$parent = $scope.$parent.$ctrl;
        this.$uibModal = $uibModal;
        this.$scope = $scope;
        this.mapService = mapService;
        this.authService = authService;
        this.mapUtilsService = mapUtilsService;
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
                this.fetchProject().then(
                    (project) => {
                        this.project = project;
                        this.loading = false;
                        this.getMap =
                            () => this.mapService.getMap(`${this.project.id}-big-preview`);
                        this.initMap();
                    },
                    () => {
                        this.$state.go('projects.list');
                    }
                );
            } else {
                this.$state.go('projects.list');
            }
        } else {
            this.populateSceneList(this.$state.params.page || 1);
            this.getMap = () => this.mapService.getMap(`${this.project.id}-map`);
            this.initMap();
        }

        this.$scope.$on('$destroy', () => {
            if (this.activeModal) {
                this.activeModal.dismiss();
            }
        });
    }

    fetchProject() {
        if (!this.projectRequest) {
            this.projectRequest = this.projectService.loadProject(this.projectId);
        }
        return this.projectRequest;
    }

    initMap() {
        this.fitProjectExtent();
        this.addProjectLayer();
    }

    addProjectLayer() {
        let url = this.projectService.getProjectLayerURL(
            this.project,
            this.authService.token()
        );
        let layer = L.tileLayer(url);

        this.getMap().then(m => {
            m.addLayer('share-layer', layer);
        });
    }

    fitProjectExtent() {
        this.getMap().then(mapWrapper => {
            this.mapUtilsService.fitMapToProject(mapWrapper, this.project, -250);
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

    populateExportList(page) {
        if (this.loadingExports) {
            return;
        }
        delete this.exportLoadingError;
        this.loadingExports = true;
        this.exportList = [];
        this.projectService.listExports({
            sort: 'createdAt, desc',
            pageSize: 10,
            page: page - 1,
            project: this.project.id
        }).then(exportResult => {
            // const replace = !this.$state.params.exportpage;
            this.lastExportResult = exportResult;
            this.numExportPaginationButtons = Math.max(6 - exportResult.page % 10, 3);
            this.currentExportPage = exportResult.page + 1;
        });
    }

    viewSceneDetail(scene) {
        this.$log.log('TODO: pop up scene preview', scene);
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
                        this.$state.go('projects.list');
                    },
                    (err) => {
                        this.$log.debug('error deleting project', err);
                    }
                );
            });
    }
    publishModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfProjectPublishModal',
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

    removeScene(scene, event) {
        if (event) {
            event.stopPropagation();
            event.preventDefault();
        }
        this.projectService.removeScenesFromProject(this.projectId, [ scene.id ]).then(
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

    openSceneDetailModal(scene) {
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

    openImportModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfSceneImportModal',
            resolve: {
                project: () => this.project
            }
        });

        this.activeModal.result.then(() => {

        });

        return this.activeModal;
    }

    cancelProjectNameEdit() {
        this.isEditingProjectName = false;
        this.editedProjectName = this.project.name;
    }

    dismissErrorMessage() {
        this.showError = false;
    }
 }

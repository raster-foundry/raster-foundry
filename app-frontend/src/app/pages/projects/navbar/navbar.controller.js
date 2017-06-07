export default class ProjectsNavbarController {
    constructor(projectService, $state, $uibModal) {
        'ngInject';

        this.projectService = projectService;
        this.$state = $state;
        this.$uibModal = $uibModal;

        this.projectId = this.$state.params.projectid;
        this.projectService.loadProject(this.projectId)
            .then((project) => {
                this.project = project;
            });
    }

    selectProjectModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfProjectSelectModal',
            resolve: {
                project: () => this.projectService.currentProject
            }
        });

        this.activeModal.result.then(p => {
            this.$state.go(this.$state.$current, { projectid: p.id });
        });

        return this.activeModal;
    }

    openPublishModal() {
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

    deleteProjectModal() {
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
}

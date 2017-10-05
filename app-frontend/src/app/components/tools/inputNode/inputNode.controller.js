export default class InputNodeController {
    constructor($uibModal, $scope, projectService) {
        'ngInject';
        this.$uibModal = $uibModal;
        this.$scope = $scope;
        this.projectService = projectService;
    }

    $onInit() {
        this.model.set('invalid', !this.allInputsDefined());
    }

    $onChanges(changes) {
        if (changes.node) {
            this.updateFromModel();
        }
    }

    updateFromModel() {
        if (this.node) {
            if (

                    this.selectedProject &&
                    this.node.projId &&
                    this.node.projId !== this.selectedProject.id ||
                    this.node.projId
            ) {
                this.projectService.get(this.node.projId).then(p => {
                    this.selectedProject = p;
                    this.checkValidity();
                });
            }
            this.selectedBand = +this.node.band;
            this.checkValidity();
        }
    }

    checkValidity() {
        this.model.set('invalid', !this.allInputsDefined());
    }

    selectProjectModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfProjectSelectModal',
            resolve: {
                project: () => this.selectedProject && this.selectedProject.id || false,
                content: () => ({title: 'Select a project'})
            }
        });

        this.activeModal.result.then(project => {
            this.selectedProject = project;
            this.onChange({project: project, band: this.selectedBand});
            this.checkValidity();
            this.$scope.$evalAsync();
        });
    }

    onBandChange() {
        this.onChange({project: this.selectedProject, band: this.selectedBand});
        this.checkValidity();
        this.$scope.$evalAsync();
    }

    allInputsDefined() {
        return this.selectedProject && typeof this.selectedBand === 'number';
    }
}

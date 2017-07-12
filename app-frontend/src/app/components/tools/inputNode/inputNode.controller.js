export default class InputNodeController {
    constructor($uibModal, $scope) {
        'ngInject';
        this.$uibModal = $uibModal;
        this.$scope = $scope;
    }

    $onInit() {
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
            this.model.set('invalid', !this.allInputsDefined());
            this.$scope.$evalAsync();
        });
    }

    onBandChange() {
        this.onChange({project: this.selectedProject, band: this.selectedBand});
        this.model.set('invalid', !this.allInputsDefined());
        this.$scope.$evalAsync();
    }

    allInputsDefined() {
        return this.selectedProject && typeof this.selectedBand === 'number';
    }
}

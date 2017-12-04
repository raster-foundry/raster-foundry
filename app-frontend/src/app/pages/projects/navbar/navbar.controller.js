export default class ProjectsNavbarController {
    constructor(projectService, projectEditService, $state, modalService, $scope) {
        'ngInject';

        this.projectService = projectService;
        this.projectEditService = projectEditService;
        this.$state = $state;
        this.modalService = modalService;
        this.$scope = $scope;
    }

    $onInit() {
        this.isEditingProjectName = false;
        this.isSavingProjectName = false;
        this.projectEditService.fetchCurrentProject()
            .then((project) => {
                this.project = project;
            });
        this.$scope.$watch('$ctrl.projectEditService.currentProject', (project) => {
            this.project = project;
        });
    }

    toggleProjectNameEdit() {
        if (this.project) {
            if (!this.isEditingProjectName) {
                this.projectEditService.fetchCurrentProject().then((project) => {
                    this.projectNameBuffer = project.name;
                    this.isEditingProjectName = !this.isEditingProjectName;
                });
            }
        }
    }

    saveProjectName() {
        this.isEditingProjectName = false;
        if (this.project && this.projectNameBuffer) {
            let lastProjectName = this.project.name;
            this.isSavingProjectName = true;
            this.project.name = this.projectNameBuffer;
            this.projectEditService.updateCurrentProject(this.project).then((project) => {
                this.project = project;
            }, () => {
                // Revert if the update fails
                this.project.name = lastProjectName;
            }).finally(() => {
                this.isSavingProjectName = false;
            });
        }
    }
}

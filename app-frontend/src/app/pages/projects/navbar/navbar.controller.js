export default class ProjectsNavbarController {
    constructor(projectService, projectEditService, $state, $uibModal) {
        'ngInject';

        this.projectService = projectService;
        this.projectEditService = projectEditService;
        this.$state = $state;
        this.$uibModal = $uibModal;
        this.projectId = this.$state.params.projectid;
    }

    $onInit() {
        this.isEditingProjectName = false;
        this.isSavingProjectName = false;
        this.projectEditService.fetchCurrentProject()
            .then((project) => {
                this.project = project;
            });
    }

    toggleProjectNameEdit() {
        if (this.project) {
            if (!this.isEditingProjectName) {
                this.projectNameBuffer = this.project.name;
            }
            this.isEditingProjectName = !this.isEditingProjectName;
        }
    }

    saveProjectName() {
        this.isEditingProjectName = false;
        if (this.project && this.projectNameBuffer) {
            let lastProjectName = this.project.name;
            this.isSavingProjectName = true;
            this.project.name = this.projectNameBuffer;
            this.projectService.updateProject(this.project).then(() => {
                // Noop, we assume this succeeds
            }, () => {
                // Revert if the update fails
                this.project.name = lastProjectName;
            }).finally(() => {
                this.isSavingProjectName = false;
            });
        }
    }
}

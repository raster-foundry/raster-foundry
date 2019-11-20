import tpl from './index.html';

class ProjectOptionsController {
    constructor($rootScope, $state, projectEditService, projectService) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }
    $onInit() {
        this.projectNameBuffer = this.project.name;
        this.projectNameConfirm = '';
        this.projectRenameInProgress = false;
        this.projectDeleteInProgress = false;
        this.NONE = false;
        this.PROJECT_RENAME = 1;
        this.PROJECT_DELETE = 2;
        this.errorType = this.NONE;
    }

    onProjectRename() {
        const previousProjectName = this.project.name;
        this.projectRenameInProgress = true;
        this.errorType = this.NONE;
        this.project.name = this.projectNameBuffer;

        this.projectEditService
            .updateCurrentProject(this.project)
            .then(
                project => {
                    this.project = project;
                    this.projectNameBuffer = this.project.name;
                },
                () => {
                    // Revert if the update fails
                    this.errorType = this.PROJECT_RENAME;
                    this.project.name = previousProjectName;
                }
            )
            .finally(() => {
                this.projectRenameInProgress = false;
            });
    }

    onProjectDelete() {
        this.projectDeleteInProgress = true;
        this.errorType = this.NONE;
        this.projectService
            .deleteProject(this.project.id)
            .then(
                () => {
                    this.$state.go('projects');
                },
                () => {
                    this.errorType = this.PROJECT_DELETE;
                }
            )
            .finally(() => {
                this.projectDeleteInProgress = false;
            });
    }
}

const component = {
    bindings: {
        project: '<'
    },
    templateUrl: tpl,
    controller: ProjectOptionsController.name
};

export default angular
    .module('components.pages.project.settings.options', [])
    .controller(ProjectOptionsController.name, ProjectOptionsController)
    .component('rfProjectOptionsPage', component).name;

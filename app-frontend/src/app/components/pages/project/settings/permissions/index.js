import tpl from './index.html';
import factory from '../../../../permissions/factory';
class ProjectPermissionsController {
    $onInit() {
        this.mainController = factory(
            this.project,
            'projects',
            'PROJECT',
            this.platform,
            false,
            () => {}
        );
    }
}

const component = {
    bindings: {
        project: '<',
        platform: '<'
    },
    templateUrl: tpl,
    controller: ProjectPermissionsController.name
};

export default angular
    .module('components.pages.projects.settings.permissions', [])
    .controller(ProjectPermissionsController.name, ProjectPermissionsController)
    .component('rfProjectPermissionsPage', component)
    .name;

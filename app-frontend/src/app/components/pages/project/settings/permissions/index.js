import tpl from './index.html';

class ProjectPermissionsController {

}

const component = {
    bindings: {
    },
    templateUrl: tpl,
    controller: ProjectPermissionsController.name
};

export default angular
    .module('components.pages.projects.settings.permissions', [])
    .controller(ProjectPermissionsController.name, ProjectPermissionsController)
    .component('rfProjectPermissionsPage', component)
    .name;

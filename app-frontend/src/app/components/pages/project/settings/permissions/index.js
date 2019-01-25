import tpl from './index.html';

class ProjectPermissionsController {

}

const component = {
    bindings: {
    },
    templateUrl: tpl,
    controller: ProjectPermissionsController.constructor.name
};

export default angular
    .module('components.pages.projects.settings.permissions', [])
    .controller(ProjectPermissionsController.constructor.name, ProjectPermissionsController)
    .component('rfProjectPermissionsPage', component)
    .name;

import tpl from './index.html';

class ProjectSettingsNavbarController {

}

const component = {
    bindings: {
    },
    templateUrl: tpl,
    controller: ProjectSettingsNavbarController.name
};

export default angular
    .module('components.projects', [])
    .controller(ProjectSettingsNavbarController.name, ProjectSettingsNavbarController)
    .component('rfProjectSettingsNavbar', component)
    .name;

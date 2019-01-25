import tpl from './index.html';

class ProjectSettingsPageController {

}

const component = {
    bindings: {
    },
    templateUrl: tpl,
    controller: ProjectSettingsPageController.constructor.name
};

export default angular
    .module('components.pages.project.settings', [])
    .controller(ProjectSettingsPageController.constructor.name, ProjectSettingsPageController)
    .component('rfProjectSettingsPage', component)
    .name;

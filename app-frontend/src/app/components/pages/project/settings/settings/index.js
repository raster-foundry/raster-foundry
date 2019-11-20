import tpl from './index.html';

class ProjectSettingsPageController {}

const component = {
    bindings: {},
    templateUrl: tpl,
    controller: ProjectSettingsPageController.name
};

export default angular
    .module('components.pages.project.settings', [])
    .controller(ProjectSettingsPageController.name, ProjectSettingsPageController)
    .component('rfProjectSettingsPage', component).name;

import tpl from './index.html';

class ProjectOptionsController {

}

const component = {
    bindings: {
    },
    templateUrl: tpl,
    controller: ProjectOptionsController.name
};

export default angular
    .module('components.pages.project.settings.options', [])
    .controller(ProjectOptionsController.name, ProjectOptionsController)
    .component('rfProjectOptionsPage', component)
    .name;

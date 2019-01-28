import tpl from './index.html';

class ProjectLayersPageController {

}

const component = {
    bindings: {
        projectId: '<'
    },
    templateUrl: tpl,
    controller: ProjectLayersPageController.name
};

export default angular
    .module('components.pages.projects.layers', [])
    .controller(ProjectLayersPageController.name, ProjectLayersPageController)
    .component('rfProjectLayersPage', component)
    .name;

import tpl from './index.html';

class ProjectListController {
}

const component = {
    bindings: {
        user: '<'
    },
    templateUrl: tpl,
    controller: ProjectListController.constructor.name
};

export default angular
    .module('components.pages.projects.list', [])
    .controller(ProjectListController.constructor.name, ProjectListController)
    .component('rfProjectListPage', component)
    .name;

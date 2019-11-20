import tpl from './index.html';

class ProjectListController {}

const component = {
    bindings: {
        user: '<'
    },
    templateUrl: tpl,
    controller: ProjectListController.name
};

export default angular
    .module('components.pages.projects.page', [])
    .controller(ProjectListController.name, ProjectListController)
    .component('rfProjectsPage', component).name;

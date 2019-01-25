import tpl from './index.html';

class ProjectPageController {
    constructor(user) {
        'ngInject';
    }
}

const component = {
    bindings: {
        projectId: '<',
        user: '<'
    },
    templateUrl: tpl,
    controller: ProjectPageController.constructor.name
};

export default angular
    .module('components.pages.project.page', [])
    .controller(ProjectPageController.constructor.name, ProjectPageController)
    .component('rfProjectPage', component)
    .name;

import tpl from './index.html';

class ProjectPageController {
    constructor(
        $rootScope
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }
}

const component = {
    bindings: {
        projectId: '<',
        user: '<'
    },
    templateUrl: tpl,
    controller: ProjectPageController.name
};

export default angular
    .module('components.pages.project.page', [])
    .controller(ProjectPageController.name, ProjectPageController)
    .component('rfProjectPage', component)
    .name;

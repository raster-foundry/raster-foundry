import tpl from './index.html';

class ProjectPublishingController {

}

const component = {
    bindings: {
    },
    templateUrl: tpl,
    controller: ProjectPublishingController.constructor.name
};

export default angular
    .module('components.pages.projects.settings.publishing', [])
    .controller(ProjectPublishingController.constructor.name, ProjectPublishingController)
    .component('rfProjectPublishingPage', component)
    .name;

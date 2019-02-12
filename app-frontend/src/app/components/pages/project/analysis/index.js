import tpl from './index.html';

class ProjectAnalysisPageController {
}

const component = {
    bindings: {},
    templateUrl: tpl,
    controller: ProjectAnalysisPageController.name
};

export default angular
    .module('components.pages.projects.analysis', [])
    .controller(ProjectAnalysisPageController.name, ProjectAnalysisPageController)
    .component('rfProjectAnalysisPage', component)
    .name;

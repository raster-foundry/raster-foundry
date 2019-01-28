import tpl from './index.html';

class AnalysesListController {

}

const component = {
    bindings: {
    },
    templateUrl: tpl,
    controller: AnalysesListController.name
};

export default angular
    .module('components.pages.project.analyses.page', [])
    .controller(AnalysesListController.name, AnalysesListController)
    .component('rfProjectAnalysesPage', component)
    .name;

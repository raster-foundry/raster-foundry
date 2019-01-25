import tpl from './index.html';

class AnalysesCompareController {

}

const component = {
    bindings: {
    },
    templateUrl: tpl,
    controller: AnalysesCompareController.constructor.name
};

export default angular
    .module('components.pages.project.analyses.compare', [])
    .controller(AnalysesCompareController.constructor.name, AnalysesCompareController)
    .component('rfProjectAnalysesComparePage', component)
    .name;

import tpl from './index.html';

class AnalysesCompareController {}

const component = {
    bindings: {},
    templateUrl: tpl,
    controller: AnalysesCompareController.name
};

export default angular
    .module('components.pages.project.analyses.compare', [])
    .controller(AnalysesCompareController.name, AnalysesCompareController)
    .component('rfProjectAnalysesComparePage', component).name;

import tpl from './index.html';

class AnalysesVisualizeController {

}

const component = {
    bindings: {
        projectId: '<'
    },
    templateUrl: tpl,
    controller: AnalysesVisualizeController.constructor.name
};

export default angular
    .module('components.pages.project.analyses.visualize', [])
    .controller(AnalysesVisualizeController.constructor.name, AnalysesVisualizeController)
    .component('rfProjectAnalysesVisualizePage', component)
    .name;

import tpl from './index.html';

class AnalysesOptionsController {

}

const component = {
    bindings: {
    },
    templateUrl: tpl,
    controller: AnalysesOptionsController.constructor.name
};

export default angular
    .module('components.pages.project.analyses.settings.options', [])
    .controller(
        AnalysesOptionsController.constructor.name,
        AnalysesOptionsController)
    .component('rfProjectAnalysesOptionsPage', component)
    .name;

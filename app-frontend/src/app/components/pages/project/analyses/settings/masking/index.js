import tpl from './index.html';

class AnalysesMaskingController {

}

const component = {
    bindings: {
    },
    templateUrl: tpl,
    controller: AnalysesMaskingController.constructor.name
};

export default angular
    .module('components.pages.project.analyses.settings.masking', [])
    .controller(
        AnalysesMaskingController.constructor.name,
        AnalysesMaskingController)
    .component('rfProjectAnalysesMaskingPage', component)
    .name;

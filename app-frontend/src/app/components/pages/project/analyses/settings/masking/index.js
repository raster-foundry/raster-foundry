import tpl from './index.html';

class AnalysesMaskingController {

}

const component = {
    bindings: {
    },
    templateUrl: tpl,
    controller: AnalysesMaskingController.name
};

export default angular
    .module('components.pages.project.analyses.settings.masking', [])
    .controller(
        AnalysesMaskingController.name,
        AnalysesMaskingController)
    .component('rfProjectAnalysesMaskingPage', component)
    .name;

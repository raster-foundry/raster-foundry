import tpl from './index.html';

class AnalysesSettingsController {

}

const component = {
    bindings: {
    },
    templateUrl: tpl,
    controller: AnalysesSettingsController.constructor.name
};

export default angular
    .module('components.pages.project.analyses.settings', [])
    .controller(AnalysesSettingsController.constructor.name, AnalysesSettingsController)
    .component('rfProjectAnalysesSettingsPage', component)
    .name;

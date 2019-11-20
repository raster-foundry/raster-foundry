import tpl from './index.html';

class AnalysesPermissionsController {}

const component = {
    bindings: {},
    templateUrl: tpl,
    controller: AnalysesPermissionsController.name
};

export default angular
    .module('components.pages.project.analyses.permissions', [])
    .controller(AnalysesPermissionsController.name, AnalysesPermissionsController)
    .component('rfProjectAnalysesPermissionsPage', component).name;

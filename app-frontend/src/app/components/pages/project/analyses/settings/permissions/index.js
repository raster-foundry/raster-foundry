import tpl from './index.html';

class AnalysesPermissionsController {

}

const component = {
    bindings: {
    },
    templateUrl: tpl,
    controller: AnalysesPermissionsController.constructor.name
};

export default angular
    .module('components.pages.project.analyses.permissions', [])
    .controller(AnalysesPermissionsController.constructor.name, AnalysesPermissionsController)
    .component('rfProjectAnalysesPermissionsPage', component)
    .name;

import tpl from './index.html';

class AnalysesPublishingController {

}

const component = {
    bindings: {
    },
    templateUrl: tpl,
    controller: AnalysesPublishingController.constructor.name
};

export default angular
    .module('components.pages.project.analyses.publishing', [])
    .controller(AnalysesPublishingController.constructor.name, AnalysesPublishingController)
    .component('rfProjectAnalysesPublishingPage', component)
    .name;

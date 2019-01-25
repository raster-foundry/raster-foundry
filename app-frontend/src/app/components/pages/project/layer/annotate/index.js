import tpl from './index.html';

class ProjectLayerAnnotateController {

}

const component = {
    bindings: {
    },
    templateUrl: tpl,
    controller: ProjectLayerAnnotateController.constructor.name
};

export default angular
    .module('components.pages.project.layer.annotate', [])
    .controller(ProjectLayerAnnotateController.constructor.name, ProjectLayerAnnotateController)
    .component('rfProjectLayerAnnotatePage', component)
    .name;

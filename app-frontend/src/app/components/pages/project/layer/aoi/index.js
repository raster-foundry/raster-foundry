import tpl from './index.html';

class ProjectLayerAoiController {

}

const component = {
    bindings: {
    },
    templateUrl: tpl,
    controller: ProjectLayerAoiController.constructor.name
};

export default angular
    .module('components.pages.project.layer.aoi', [])
    .controller(ProjectLayerAoiController.constructor.name, ProjectLayerAoiController)
    .component('rfProjectLayerAoiPage', component)
    .name;

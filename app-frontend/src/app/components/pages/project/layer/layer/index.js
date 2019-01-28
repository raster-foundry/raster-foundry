import tpl from './index.html';

class ProjectLayerController {

}

const component = {
    bindings: {
        projectId: '<',
        layerId: '<'
    },
    templateUrl: tpl,
    controller: ProjectLayerController.name
};

export default angular
    .module('components.pages.project.layer.page', [])
    .controller(ProjectLayerController.name, ProjectLayerController)
    .component('rfProjectLayerPage', component)
    .name;

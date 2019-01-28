import tpl from './index.html';

class LayerAnnotationsController {

}

const component = {
    bindings: {
    },
    templateUrl: tpl,
    controller: LayerAnnotationsController.name
};

export default angular
    .module('components.pages.project.layer.annotations', [])
    .controller(LayerAnnotationsController.name, LayerAnnotationsController)
    .component('rfProjectLayerAnnotationsPage', component)
    .name;

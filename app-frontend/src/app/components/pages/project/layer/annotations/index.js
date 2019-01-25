import tpl from './index.html';

class LayerAnnotationsController {

}

const component = {
    bindings: {
    },
    templateUrl: tpl,
    controller: LayerAnnotationsController.constructor.name
};

export default angular
    .module('components.pages.project.layer.annotations', [])
    .controller(LayerAnnotationsController.constructor.name, LayerAnnotationsController)
    .component('rfProjectLayerAnnotationsPage', component)
    .name;

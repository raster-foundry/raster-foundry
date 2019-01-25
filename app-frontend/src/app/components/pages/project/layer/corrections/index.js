import tpl from './index.html';

class LayerCorrectionsController {

}

const component = {
    bindings: {
    },
    templateUrl: tpl,
    controller: LayerCorrectionsController.constructor.name
};

export default angular
    .module('components.pages.project.layer.corrections', [])
    .controller(LayerCorrectionsController.constructor.name, LayerCorrectionsController)
    .component('rfProjectLayerCorrectionsPage', component)
    .name;

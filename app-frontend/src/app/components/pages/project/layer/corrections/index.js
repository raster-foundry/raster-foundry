import tpl from './index.html';

class LayerCorrectionsController {

}

const component = {
    bindings: {
    },
    templateUrl: tpl,
    controller: LayerCorrectionsController.name
};

export default angular
    .module('components.pages.project.layer.corrections', [])
    .controller(LayerCorrectionsController.name, LayerCorrectionsController)
    .component('rfProjectLayerCorrectionsPage', component)
    .name;

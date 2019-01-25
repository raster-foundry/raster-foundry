import tpl from './index.html';

class LayerColormodeController {

}

const component = {
    bindings: {
    },
    templateUrl: tpl,
    controller: LayerColormodeController.constructor.name
};

export default angular
    .module('components.pages.project.layer.colormode', [])
    .controller(LayerColormodeController.constructor.name, LayerColormodeController)
    .component('rfProjectLayerColormodePage', component)
    .name;

import tpl from './index.html';

class LayerExportsController {

}

const component = {
    bindings: {
    },
    templateUrl: tpl,
    controller: LayerExportsController.constructor.name
};

export default angular
    .module('components.pages.project.layer.exports', [])
    .controller(LayerExportsController.constructor.name, LayerExportsController)
    .component('rfProjectLayerExportsPage', component)
    .name;

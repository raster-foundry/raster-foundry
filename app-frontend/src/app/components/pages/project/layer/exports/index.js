import tpl from './index.html';

class LayerExportsController {

}

const component = {
    bindings: {
    },
    templateUrl: tpl,
    controller: LayerExportsController.name
};

export default angular
    .module('components.pages.project.layer.exports', [])
    .controller(LayerExportsController.name, LayerExportsController)
    .component('rfProjectLayerExportsPage', component)
    .name;

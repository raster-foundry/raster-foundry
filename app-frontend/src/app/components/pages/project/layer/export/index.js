import tpl from './index.html';

class LayerExportCreateController {

}

const component = {
    bindings: {
    },
    templateUrl: tpl,
    controller: LayerExportCreateController.constructor.name
};

export default angular
    .module('components.pages.project.layer.export', [])
    .controller(LayerExportCreateController.constructor.name, LayerExportCreateController)
    .component('rfProjectLayerExportPage', component)
    .name;

import tpl from './index.html';

class LayerExportCreateController {

}

const component = {
    bindings: {
    },
    templateUrl: tpl,
    controller: LayerExportCreateController.name
};

export default angular
    .module('components.pages.project.layer.export', [])
    .controller(LayerExportCreateController.name, LayerExportCreateController)
    .component('rfProjectLayerExportPage', component)
    .name;

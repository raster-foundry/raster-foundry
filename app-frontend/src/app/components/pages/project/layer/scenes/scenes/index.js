import tpl from './index.html';

class LayerScenesController {

}

const component = {
    bindings: {
    },
    templateUrl: tpl,
    controller: LayerScenesController.name
};

export default angular
    .module('components.pages.project.scenes.page', [])
    .controller(LayerScenesController.name, LayerScenesController)
    .component('rfProjectLayerScenesPage', component)
    .name;

import tpl from './index.html';

class LayerScenesController {

}

const component = {
    bindings: {
    },
    templateUrl: tpl,
    controller: LayerScenesController.constructor.name
};

export default angular
    .module('components.pages.project.scenes.page', [])
    .controller(LayerScenesController.constructor.name, LayerScenesController)
    .component('rfProjectLayerScenesPage', component)
    .name;

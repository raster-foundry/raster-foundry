import tpl from './index.html';

class LayerScenesBrowseController {

}

const component = {
    bindings: {
    },
    templateUrl: tpl,
    controller: LayerScenesBrowseController.constructor.name
};

export default angular
    .module('components.pages.project.layers.scenes.browse', [])
    .controller(LayerScenesBrowseController.constructor.name, LayerScenesBrowseController)
    .component('rfProjectLayerScenesBrowsePage', component)
    .name;

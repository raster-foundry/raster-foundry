import tpl from './index.html';

class LayerScenesBrowseController {

}

const component = {
    bindings: {
    },
    templateUrl: tpl,
    controller: LayerScenesBrowseController.name
};

export default angular
    .module('components.pages.project.layers.scenes.browse', [])
    .controller(LayerScenesBrowseController.name, LayerScenesBrowseController)
    .component('rfProjectLayerScenesBrowsePage', component)
    .name;

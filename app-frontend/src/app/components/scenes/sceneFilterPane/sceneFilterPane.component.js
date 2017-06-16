import sceneFilterTpl from './sceneFilterPane.html';

const rfSceneFilterPane = {
    templateUrl: sceneFilterTpl,
    controller: 'SceneFilterPaneController',
    bindings: {
        filters: '=',
        opened: '='
    }
};

export default rfSceneFilterPane;

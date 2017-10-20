import sceneFilterTpl from './sceneFilterPane.html';

const rfSceneFilterPane = {
    templateUrl: sceneFilterTpl,
    controller: 'SceneFilterPaneController',
    bindings: {
        filters: '<',
        opened: '<',
        onFilterChange: '&',
        onCloseFilterPane: '&'
    }
};

export default rfSceneFilterPane;

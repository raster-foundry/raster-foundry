// Component code
import sceneTpl from './sceneItem.html';

const rfSceneItem = {
    templateUrl: sceneTpl,
    controller: 'SceneItemController',
    bindings: {
        scene: '<',
        selected: '&',
        onSelect: '&'
    }
};

export default rfSceneItem;

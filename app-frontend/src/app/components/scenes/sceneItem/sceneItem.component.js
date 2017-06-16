// Component code
import sceneTpl from './sceneItem.html';

const rfSceneItem = {
    templateUrl: sceneTpl,
    transclude: true,
    controller: 'SceneItemController',
    bindings: {
        scene: '<',
        selected: '&',
        isDisabled: '<',
        onSelect: '&',
        onAction: '&',
        onView: '&',
        onDownload: '&',
        actionIcon: '<'
    }
};

export default rfSceneItem;

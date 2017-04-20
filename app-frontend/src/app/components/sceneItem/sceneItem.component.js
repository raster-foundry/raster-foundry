// Component code
import sceneTpl from './sceneItem.html';

const rfSceneItem = {
    templateUrl: sceneTpl,
    transclude: true,
    controller: 'SceneItemController',
    bindings: {
        scene: '<',
        selected: '&',
<<<<<<< HEAD
        isDisabled: '<',
=======
        isInProject: '<',
>>>>>>> Disallow adding scenes already added to project
        onSelect: '&',
        onAction: '&',
        onView: '&',
        onDownload: '&',
        actionIcon: '<'
    }
};

export default rfSceneItem;

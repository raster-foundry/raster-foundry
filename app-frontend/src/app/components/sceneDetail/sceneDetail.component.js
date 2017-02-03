// Component code
import sceneDetailTpl from './sceneDetail.html';
const rfSceneDetail = {
    templateUrl: sceneDetailTpl,
    bindings: {
        scene: '<',
        showThumbnail: '<',
        isSelected: '<',
        onCloseClick: '&',
        onSelectClick: '&'
    },
    replace: true,
    controller: 'SceneDetailComponentController'
};

export default rfSceneDetail;

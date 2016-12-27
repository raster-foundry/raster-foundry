// Component code
import sceneDetailTpl from './sceneDetail.html';
const rfSceneDetail = {
    templateUrl: sceneDetailTpl,
    bindings: {
        scene: '<',
        showThumbnail: '<'
    },
    replace: true,
    controller: 'SceneDetailComponentController'
};

export default rfSceneDetail;

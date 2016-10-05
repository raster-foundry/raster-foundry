// Component code
import sceneDetailTpl from './sceneDetail.html';

const rfSceneItem = {
    templateUrl: sceneDetailTpl,
    controller: 'RfSceneDetailController',
    bindings: {
        scene: '<',
        withMap: '<?'
    },
    replace: true
};

export default rfSceneItem;

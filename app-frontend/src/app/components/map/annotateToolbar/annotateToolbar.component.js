import annotateToolbarTpl from './annotateToolbar.html';

const annotateToolbar = {
    templateUrl: annotateToolbarTpl,
    controller: 'AnnotateToolbarController',
    bindings: {
        mapId: '@',
        isLabeling: '<',
        onShapeCreating: '&',
        onShapeCreated: '&'
    }
};

export default annotateToolbar;

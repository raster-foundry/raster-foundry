import annotateToolbarTpl from './annotateToolbar.html';

const annotateToolbar = {
    templateUrl: annotateToolbarTpl,
    controller: 'AnnotateToolbarController',
    bindings: {
        mapId: '@',
        disableToolbarAction: '<',
        onShapeCreating: '&',
        onShapeCreated: '&'
    }
};

export default annotateToolbar;

import annotateToolbarTpl from './annotateToolbar.html';

const annotateToolbar = {
    templateUrl: annotateToolbarTpl,
    controller: 'AnnotateToolbarController',
    bindings: {
        mapId: '@',
        disableToolbarAction: '<',
        bulkMode: '<',
        onDrawingCanceled: '&',
        onShapeCreating: '&',
        onShapeCreated: '&'
    }
};

export default annotateToolbar;

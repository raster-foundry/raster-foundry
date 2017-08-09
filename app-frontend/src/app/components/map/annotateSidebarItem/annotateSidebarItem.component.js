import annotateSidebarItemTpl from './annotateSidebarItem.html';

const annotateSidebarItem = {
    templateUrl: annotateSidebarItemTpl,
    controller: 'AnnotateSidebarItemController',
    bindings: {
        mapId: '@',
        data: '<',
        annotation: '<',
        shapeType: '<',
        labelInputs: '<',
        onCancelAddAnnotation: '&',
        onAddAnnotation: '&',
        onUpdateAnnotationStart: '&',
        onDeleteAnnotation: '&',
        onCancelUpdateAnnotation: '&',
        onUpdateAnnotationFinish: '&'
    }
};

export default annotateSidebarItem;

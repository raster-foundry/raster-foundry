import annotateSidebarItemTpl from './annotateSidebarItem.html';

const annotateSidebarItem = {
    templateUrl: annotateSidebarItemTpl,
    controller: 'AnnotateSidebarItemController',
    bindings: {
        annotation: '<',
        labelInputs: '<',
        editId: '<',
        disableSidebarAction: '<',
        onCloneAnnotation: '&',
        onUpdateAnnotationStart: '&',
        onDeleteAnnotation: '&',
        onUpdateAnnotationFinish: '&',
        onCancelUpdateAnnotation: '&'
    }
};

export default annotateSidebarItem;

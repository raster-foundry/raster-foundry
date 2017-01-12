import diagramContainerTpl from './diagramContainer.html';

export default {
    templateUrl: diagramContainerTpl,
    controller: 'DiagramContainerController',
    bindings: {
        shapes: '<?',
        cellLabel: '<?',
        onCellClick: '&',
        onPaperClick: '&'
    }
};

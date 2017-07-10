import diagramContainerTpl from './diagramContainer.html';

export default {
    templateUrl: diagramContainerTpl,
    controller: 'DiagramContainerController2',
    bindings: {
        onPreview: '&',
        onShare: '&',
        onParameterChange: '&',
        toolDefinition: '<',
        toolParameters: '<'
    }
};

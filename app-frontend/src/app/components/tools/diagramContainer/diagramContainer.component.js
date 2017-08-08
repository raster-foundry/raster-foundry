import diagramContainerTpl from './diagramContainer.html';

export default {
    templateUrl: diagramContainerTpl,
    controller: 'DiagramContainerController',
    bindings: {
        onPreview: '&',
        onShare: '&',
        onParameterChange: '&',
        onGraphComplete: '&',
        toolDefinition: '<',
        toolParameters: '<'
    }
};

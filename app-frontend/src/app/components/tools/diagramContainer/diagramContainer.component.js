import diagramContainerTpl from './diagramContainer.html';

export default {
    templateUrl: diagramContainerTpl,
    controller: 'DiagramContainerController',
    bindings: {
        toolDefinition: '<',
        toolParameters: '<',
        toolrun: '<',
        onPreview: '&',
        onShare: '&',
        onParameterChange: '&',
        onGraphComplete: '&'
    }
};

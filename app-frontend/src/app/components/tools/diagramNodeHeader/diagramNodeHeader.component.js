import diagramNodeHeaderTpl from './diagramNodeHeader.html';

export default {
    templateUrl: diagramNodeHeaderTpl,
    controller: 'DiagramNodeHeaderController',
    bindings: {
        model: '<',
        invalid: '<'
    }
};

import operationNodeTpl from './operationNode.html';

export default {
    templateUrl: operationNodeTpl,
    controller: 'OperationNodeController',
    bindings: {
        model: '<',
        onChange: '&'
    }
};

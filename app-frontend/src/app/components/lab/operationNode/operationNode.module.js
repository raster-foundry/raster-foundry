import angular from 'angular';
import operationNodeTpl from './operationNode.html';
import NodeUtils from '_redux/node-utils';

const OperationNodeComponent = {
    templateUrl: operationNodeTpl,
    controller: 'OperationNodeController',
    bindings: {
        nodeId: '<'
    }
};

class OperationNodeController {
    constructor($scope, $ngRedux) {
        'ngInject';
        this.$scope = $scope;

        let unsubscribe = $ngRedux.connect(
            this.mapStateToThis.bind(this)
        )(this);
        $scope.$on('$destroy', unsubscribe);
    }

    mapStateToThis(state) {
        const node = NodeUtils.getNodeDefinition(state, this);
        if (!node) {
            return {};
        }
        const inputs = node.inputIds.map((nodeId) => {
            let inputNode = NodeUtils.getNodeDefinition(state, {nodeId});
            return inputNode.metadata.label;
        });
        return {
            node,
            inputs
        };
    }
}

const OperationNodeModule = angular.module('components.lab.operationNode', []);

OperationNodeModule.component('rfOperationNode', OperationNodeComponent);
OperationNodeModule.controller('OperationNodeController', OperationNodeController);

export default OperationNodeModule;

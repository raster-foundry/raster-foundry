import angular from 'angular';
import operationNodeTpl from './operationNode.html';
import { getNodeDefinition } from '_redux/node-utils';

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
        const node = getNodeDefinition(state, this);
        if (!node) {
            return {};
        }
        const inputs = node.args.map((nodeId) => {
            let inputNode = getNodeDefinition(state, {nodeId});
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

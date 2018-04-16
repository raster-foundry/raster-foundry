import angular from 'angular';
import constantNodeTpl from './constantNode.html';

import NodeActions from '_redux/actions/node-actions';
import NodeUtils from '_redux/node-utils';

const ConstantNodeComponent = {
    templateUrl: constantNodeTpl,
    controller: 'ConstantNodeController',
    bindings: {
        nodeId: '<'
    }
};

class ConstantNodeController {
    constructor($scope, $ngRedux) {
        'ngInject';

        let unsubscribe = $ngRedux.connect(
            this.mapStateToThis.bind(this),
            NodeActions
        )(this);
        $scope.$on('$destroy', unsubscribe);

        $scope.$watch('$ctrl.node', (node) => {
            if (node && node.constant) {
                this.value = +node.constant;
            }
        });
    }

    mapStateToThis(state) {
        return {
            node: NodeUtils.getNodeDefinition(state, this)
        };
    }

    resetValue() {
        if (typeof this.node.metadata.default !== 'undefined') {
            this.value = this.node.metadata.default;
            this.onValueChange();
        }
    }

    onValueChange() {
        let updatedNode = Object.assign({}, this.node, {
            constant: this.value
        });
        if (!updatedNode.metadata.default) {
            updatedNode.metadata = Object.assign({}, this.node.metadata, {
                default: this.node.constant
            });
        }

        this.updateNode(updatedNode);
    }
}


const ConstantNodeModule = angular.module('components.lab.constantNode', []);

ConstantNodeModule.component('rfConstantNode', ConstantNodeComponent);
ConstantNodeModule.controller('ConstantNodeController', ConstantNodeController);

export default ConstantNodeModule;

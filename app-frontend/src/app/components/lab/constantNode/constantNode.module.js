import angular from 'angular';
import constantNodeTpl from './constantNode.html';

import NodeActions from '_redux/actions/node-actions';
import {getNodeDefinition} from '_redux/node-utils';

const ConstantNodeComponent = {
    templateUrl: constantNodeTpl,
    controller: 'ConstantNodeController',
    bindings: {
        nodeId: '<'
    }
};

class ConstantNodeController {
    constructor($rootScope, $scope, $ngRedux) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    mapStateToThis(state) {
        return {
            node: getNodeDefinition(state, this)
        };
    }

    $onInit() {
        let unsubscribe = this.$ngRedux.connect(
            this.mapStateToThis.bind(this),
            NodeActions
        )(this);
        this.$scope.$on('$destroy', unsubscribe);

        this.$scope.$watch('$ctrl.node', (node) => {
            if (node && node.constant) {
                this.value = +node.constant;
            }
        });
    }

    resetValue() {
        if (typeof this.node.metadata.default !== 'undefined') {
            this.value = this.node.metadata.default;
            this.onValueChange();
        }
    }

    onValueChange() {
        let payload;
        if (!this.node.metadata.default) {
            payload = Object.assign({}, this.node, {
                constant: this.value,
                metadata: Object.assign({}, this.node.metadata, {
                    default: this.node.constant
                })
            });
        } else {
            payload = Object.assign({}, this.node, {
                constant: this.value
            });
        }

        this.updateNode({
            payload,
            hard: true
        });
    }
}


const ConstantNodeModule = angular.module('components.lab.constantNode', []);

ConstantNodeModule.component('rfConstantNode', ConstantNodeComponent);
ConstantNodeModule.controller('ConstantNodeController', ConstantNodeController);

export default ConstantNodeModule;

import angular from 'angular';

import diagramNodeHeaderTpl from './diagramNodeHeader.html';
import LabActions from '_redux/actions/lab-actions';
import NodeActions from '_redux/actions/node-actions';
import { getNodeDefinition } from '_redux/node-utils';

const DiagramNodeHeaderComponent = {
    templateUrl: diagramNodeHeaderTpl,
    controller: 'DiagramNodeHeaderController',
    bindings: {
        nodeId: '<'
    }
};

class DiagramNodeHeaderController {
    constructor($document, $scope, $ngRedux) {
        'ngInject';
        this.$document = $document;
        this.$scope = $scope;

        let unsubscribe = $ngRedux.connect(
            this.mapStateToThis.bind(this),
            Object.assign({}, LabActions, NodeActions)
        )(this);
        $scope.$on('$destroy', unsubscribe);
    }

    mapStateToThis(state) {
        return {
            readonly: state.lab.readonly,
            previewNodes: state.lab.previewNodes,
            analysisErrors: state.lab.analysisErrors,
            node: getNodeDefinition(state, this)
        };
    }

    get typeMap() {
        return {
            'function': 'Function',
            'projectSrc': 'Input',
            'const': 'Constant',
            'classify': 'Classify'
        };
    }

    toggleMenu() {
        let initialClick = true;
        const onClick = () => {
            if (!initialClick) {
                this.showMenu = false;
                this.$document.off('click', this.clickListener);
                this.$scope.$evalAsync();
            } else {
                initialClick = false;
            }
        };
        if (!this.showMenu) {
            this.showMenu = true;
            this.clickListener = onClick;
            this.$document.on('click', onClick);
        } else {
            this.showMenu = false;
            this.$document.off('click', this.clickListener);
            delete this.clickListener;
        }
    }

    setHeight(height) {
        this.model.set('size', {width: 300, height: height});
    }

    toggleNodeLabelEdit() {
        this.isEditingNodeLabel = !this.isEditingNodeLabel;
    }

    finishNodelabelEdit() {
        if (this.nameBuffer) {
            this.node = Object.assign(this.node, {metadata: {label: this.nameBuffer}});
            this.updateNode({ payload: this.node, hard: true});
            this.isEditingNodeLabel = !this.isEditingNodeLabel;
        }
    }
}

const DiagramNodeHeaderModule = angular.module('components.lab.diagramNodeHeader', []);
DiagramNodeHeaderModule.component('rfDiagramNodeHeader', DiagramNodeHeaderComponent);
DiagramNodeHeaderModule.controller('DiagramNodeHeaderController', DiagramNodeHeaderController);

export default DiagramNodeHeaderModule;

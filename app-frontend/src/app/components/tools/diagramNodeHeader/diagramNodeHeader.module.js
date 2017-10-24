import angular from 'angular';

import diagramNodeHeaderTpl from './diagramNodeHeader.html';
import LabActions from '../../../redux/actions/lab-actions';
import { getNodeDefinition } from '../../../redux/node-utils';

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

        let unsubscribe = $ngRedux.connect(this.mapStateToThis.bind(this), LabActions)(this);
        $scope.$on('$destroy', unsubscribe);
    }

    mapStateToThis(state) {
        return {
            readonly: state.lab.readonly,
            previewNodes: state.lab.previewNodes,
            toolErrors: state.lab.toolErrors,
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
}

const DiagramNodeHeaderModule = angular.module(
    'components.tools.diagramNodeHeader',
    []
);

DiagramNodeHeaderModule.component(
    'rfDiagramNodeHeader', DiagramNodeHeaderComponent
);

DiagramNodeHeaderModule.controller(
    'DiagramNodeHeaderController', DiagramNodeHeaderController
);

export default DiagramNodeHeaderModule;

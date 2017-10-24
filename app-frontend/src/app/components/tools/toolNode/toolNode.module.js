import angular from 'angular';

import toolNodeTpl from './toolNode.html';
import LabActions from '../../../redux/actions/lab-actions';
import NodeActions from '../../../redux/actions/node-actions';
import { getNodeDefinition } from '../../../redux/node-utils';

class ToolNodeController {
    constructor($ngRedux, $scope, $log) {
        'ngInject';
        this.$log = $log;

        let unsubscribe = $ngRedux.connect(
            this.mapStateToThis.bind(this),
            Object.assign({}, LabActions, NodeActions)
        )(this);
        $scope.$on('$destroy', unsubscribe);
        $scope.$watch('$ctrl.readonly', (readonly) => {
            if (readonly && !this.isCollapsed) {
                this.toggleCollapse();
            }
        });
    }

    mapStateToThis(state) {
        return {
            readonly: state.lab.readonly,
            tool: state.lab.tool,
            selectedNode: state.lab.selectedNode,
            toolErrors: state.lab.toolErrors,
            node: getNodeDefinition(state, this)
        };
    }

    $onInit() {
        // Acceptable values are 'BODY', 'HISTOGRAM', and 'STATISTICS'
        this.currentView = 'BODY';
        this.isCollapsed = false;
        this.baseWidth = 400;
        this.histogramHeight = 250;
        this.statisticsHeight = 260;
        if (this.ifCellType('const')) {
            this.model.resize(this.baseWidth, 125);
        } else if (this.ifCellType('classify')) {
            this.model.resize(this.baseWidth, 275);
        }
    }

    preview() {
        this.previewNode(this.nodeId);
    }

    toggleHistogram() {
        if (this.isCollapsed) {
            this.toggleCollapse();
        }
        if (this.currentView === 'BODY' && !this.bodyHeight) {
            this.bodyHeight = this.model.getBBox().height;
        }
        if (this.currentView === 'HISTOGRAM') {
            this.currentView = 'BODY';
            this.model.resize(this.baseWidth, this.bodyHeight);
        } else {
            this.currentView = 'HISTOGRAM';
            this.expandedSize = this.model.getBBox();
            this.model.resize(this.baseWidth, this.histogramHeight);
        }
    }

    toggleStatistics() {
        if (this.isCollapsed) {
            this.toggleCollapse();
        }
        if (this.currentView === 'BODY' && !this.bodyHeight) {
            this.bodyHeight = this.model.getBBox().height;
        }
        if (this.currentView === 'STATISTICS') {
            this.currentView = 'BODY';
            this.model.resize(this.baseWidth, this.bodyHeight);
        } else {
            this.currentView = 'STATISTICS';
            this.model.resize(this.baseWidth, this.statisticsHeight);
        }
    }

    toggleCollapse() {
        if (this.currentView === 'BODY' && !this.bodyHeight) {
            this.bodyHeight = this.model.getBBox().height;
        }
        if (this.isCollapsed) {
            this.model.resize(this.baseWidth, this.lastSize.height);
            this.isCollapsed = false;
        } else {
            this.lastSize = this.model.getBBox();
            this.model.resize(this.baseWidth, 50);
            this.isCollapsed = true;
        }
    }

    toggleBody() {
        this.showBody = !this.showBody;
        if (!this.showBody) {
            if (!this.showHistogram) {
                this.expandedSize = this.model.getBBox();
            }
            this.model.resize(this.expandedSize.width, 50);
        } else if (this.showHistogram) {
            this.model.resize(this.expandedSize.width, this.histogramHeight);
        } else {
            this.model.resize(this.expandedSize.width, this.expandedSize.height);
        }
    }

    ifCellType(type) {
        return this.model.get('cellType') === type;
    }

    showCellBody() {
        return (
            this.currentView === 'BODY' &&
                !this.isCollapsed
        );
    }
}

const ToolNodeComponent = {
    templateUrl: toolNodeTpl,
    controller: ToolNodeController,
    bindings: {
        nodeId: '<',
        model: '<'
    }
};

const ToolNodeModule = angular.module('components.tool.toolnode', []);
ToolNodeModule.component('rfToolNode', ToolNodeComponent);
export default ToolNodeModule;

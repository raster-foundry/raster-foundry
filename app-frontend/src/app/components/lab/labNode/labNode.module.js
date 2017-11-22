import angular from 'angular';

import labNodeTpl from './labNode.html';
import LabActions from '_redux/actions/lab-actions';
import NodeActions from '_redux/actions/node-actions';
import { getNodeDefinition } from '_redux/node-utils';

class LabNodeController {
    constructor($ngRedux, $scope, $log, $element) {
        'ngInject';
        this.$log = $log;
        this.$element = $element;

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
        $scope.$watch('$ctrl.selectingNode', (selectingNode) => {
            if (selectingNode) {
                this.$element.addClass('selectable');
            } else {
                this.$element.removeClass('selectable');
            }
        });
        $scope.$watch('$ctrl.selectedNode', (selectedNode) => {
            if (selectedNode === this.nodeId) {
                this.$element.addClass('selected');
            } else {
                this.$element.removeClass('selected');
            }
        });
    }

    $postLink() {
        this.$element.bind('click', this.onNodeClick.bind(this));
    }

    mapStateToThis(state) {
        return {
            readonly: state.lab.readonly,
            analysis: state.lab.analysis,
            selectingNode: state.lab.selectingNode,
            selectedNode: state.lab.selectedNode,
            analysisErrors: state.lab.analysisErrors,
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
        if (!this.selectingNode) {
            this.selectNode(this.nodeId);
        }
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

    onNodeClick(event) {
        if (this.selectingNode && this.selectedNode !== this.nodeId) {
            event.stopPropagation();
            this.selectNode(this.nodeId);
        }
    }
}

const LabNodeComponent = {
    templateUrl: labNodeTpl,
    controller: LabNodeController,
    bindings: {
        nodeId: '<',
        model: '<'
    }
};

const LabNodeModule = angular.module('components.lab.labnode', []);
LabNodeModule.component('rfLabNode', LabNodeComponent);
export default LabNodeModule;

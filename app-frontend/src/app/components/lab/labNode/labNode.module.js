import angular from 'angular';
import _ from 'lodash';

import labNodeTpl from './labNode.html';

import NodeActions from '_redux/actions/node-actions';
import { getNodeDefinition, nodeIsChildOf } from '_redux/node-utils';

class LabNodeController {
    constructor($ngRedux, $scope, $log, $element, modalService, tokenService,
                projectService, APP_CONFIG) {
        'ngInject';
        this.$log = $log;
        this.$element = $element;
        this.modalService = modalService;
        this.tokenService = tokenService;
        this.projectService = projectService;

        this.tileServer = `${APP_CONFIG.tileServerLocation}`;

        let unsubscribe = $ngRedux.connect(
            this.mapStateToThis.bind(this),
            NodeActions
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
        $scope.$watch('$ctrl.linkNode', (linkNode) => {
            if (linkNode && this.isLinkable(linkNode)) {
                this.$element.addClass('linkable');
            } else if (linkNode && !this.isLinkable(linkNode)) {
                this.$element.addClass('unlinkable');
            } else {
                this.$element.removeClass('linkable');
                this.$element.removeClass('unlinkable');
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
        const workspace = state.lab.workspace;
        const node = getNodeDefinition(state, this);
        const analysisId = node && node.analysisId;
        const analysis = workspace.analyses.find((a) => a.id === analysisId);

        const selector = {
            readonly: state.lab.readonly,
            selectingNode: state.lab.selectingNode,
            selectedNode: state.lab.selectedNode,
            errors: state.lab.errors,
            workspace,
            node,
            nodes: state.lab.nodes,
            analysis,
            linkNode: state.lab.linkNode
        };
        return selector;
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
        if (!this.selectingNode && this.nodeId) {
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
        if (this.linkNode && this.isLinkable(this.linkNode)) {
            event.stopPropagation();
            this.finishLinkingNodes(this.nodeId);
        }
    }

    onNodeShare() {
        const nodeType = this.model.get('cellType');
        if (this.node) {
            if (nodeType === 'projectSrc') {
                this.tokenService.getOrCreateAnalysisMapToken({
                    organizationId: this.analysis.organizationId,
                    name: this.analysis.name + ' - ' + this.analysis.id,
                    project: this.node.projId
                }).then((mapToken) => {
                    this.publishModal(
                        this.projectService.getProjectLayerURL(
                            this.node.projId, {mapToken: mapToken.id}
                        )
                    );
                });
            } else {
                this.tokenService.getOrCreateAnalysisMapToken({
                    organizationId: this.analysis.organizationId,
                    name: this.analysis.name + ' - ' + this.analysis.id,
                    analysis: this.analysis.id
                }).then((mapToken) => {
                    this.publishModal(
                        // eslint-disable-next-line max-len
                        `${this.tileServer}/tools/${this.analysis.id}/{z}/{x}/{y}?mapToken=${mapToken.id}&node=${this.nodeId}`
                    );
                });
            }
        }
    }

    publishModal(tileUrl) {
        if (tileUrl) {
            this.modalService.open({
                component: 'rfProjectPublishModal',
                resolve: {
                    tileUrl: () => tileUrl,
                    noDownload: () => true,
                    templateTitle: () => this.analysis.name
                }
            });
        }
        return false;
    }

    isLinkable(otherNode) {
        return otherNode !== this.nodeId &&
            this.ifCellType('function') &&
            !this.node.args.includes(otherNode) &&
            !nodeIsChildOf(this.nodeId, otherNode, this.nodes);
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

/* global joint */
import angular from 'angular';
import _ from 'lodash';
import diagramContainerTpl from './diagramContainer.html';

import LabActions from '_redux/actions/lab-actions';
import NodeActions from '_redux/actions/node-actions';
import { nodesFromAst, getNodeDefinition } from '_redux/node-utils';

const DiagramContainerComponent = {
    templateUrl: diagramContainerTpl,
    controller: 'DiagramContainerController'
};

const maxZoom = 3;
const minZoom = 0.025;

class DiagramContainerController {
    constructor( // eslint-disable-line max-params
        $element, $scope, $state, $timeout, $compile, $document, $window, $rootScope,
        mousetipService, labUtils, $ngRedux
    ) {
        'ngInject';
        this.$element = $element;
        this.$scope = $scope;
        this.$rootScope = $rootScope;
        this.$state = $state;
        this.$timeout = $timeout;
        this.$compile = $compile;
        this.$document = $document;
        this.$window = $window;
        this.mousetipService = mousetipService;
        this.labUtils = labUtils;

        let unsubscribe = $ngRedux.connect(
            this.mapStateToThis,
            Object.assign({}, LabActions, NodeActions)
        )(this);
        $scope.$on('$destroy', unsubscribe);

        this.debouncedUpdateNode = _.debounce(this.updateNode, 300);
    }

    mapStateToThis(state) {
        return {
            analysis: state.lab.analysis,
            readonly: state.lab.readonly,
            selectingNode: state.lab.selectingNode,
            selectedNode: state.lab.selectedNode,
            preventSelecting: state.lab.preventSelecting,
            analysisErrors: state.lab.analysisErrors,
            reduxState: state
        };
    }

    $onInit() {
        this.scale = 1;

        this.$scope.$on('$destroy', this.$onDestroy.bind(this));

        this.workspaceElement = this.$element[0].children[0];
        this.comparison = [false, false];
        this.cellDimensions = {width: 400, height: 200 };
        this.paddingFactor = 0.8;
        this.nodeSeparationFactor = 0.2;
        this.panActive = false;
        // TODO nodes determine context menu options by type and redux state

        this.contextInitialized = true;

        const analysisWatch = this.$scope.$watch('$ctrl.analysis', (analysis) => {
            if (analysis && !this.shapes) {
                this.initDiagram();
            } else {
                analysisWatch();
            }
        });
    }

    $onDestroy() {
        if (this.onWindowResize) {
            this.$window.removeEventListener('resize', this.onWindowResize);
        }
    }

    canPreview() {
        return !this.preventSelecting && !(this.analysisErrors && this.analysisErrors.size);
    }

    initDiagram() {
        let definition = this.analysis.executionParameters ?
            this.analysis.executionParameters :
            this.analysis;

        let extract = this.labUtils.extractShapes(
            definition,
            this.cellDimensions
        );
        let labNodes = nodesFromAst(definition);
        this.initNodes(labNodes);

        this.shapes = extract.shapes;
        this.shapes.forEach((shape) => {
            shape.on('change:position', (node, position) => {
                if (this.nodePositionsSet) {
                    this.onShapeMove(shape.id, position);
                }
            });
        });
        this.nodes = extract.nodes;

        if (!this.graph) {
            this.graph = new joint.dia.Graph();
        } else {
            this.graph.clear();
        }

        if (!this.paper) {
            let el = $(this.$element);
            let height = el.height();
            if (height === 0) {
                let resetDimensions = () => {
                    let elHeight = el.height();
                    if (elHeight !== 0) {
                        this.paper.setDimensions(el.width(), elHeight);
                        this.scaleToContent();
                    } else {
                        this.$timeout(resetDimensions, 500);
                    }
                };
                this.$scope.$evalAsync(resetDimensions);
            }
            this.paper = new joint.dia.Paper({
                el: this.workspaceElement,
                height: height,
                width: el.width(),
                gridSize: 25,
                drawGrid: {
                    name: 'doubleMesh',
                    args: [{
                        thickness: 1,
                        scaleFactor: 6
                    }, {
                        color: 'lightgrey',
                        thickness: 1,
                        scaleFactor: 6
                    }]
                },
                model: this.graph,
                clickThreshold: 4,
                interactive: true
            });
            this.paper.drawGrid({
                color: '#aaa',
                thickness: 1
            });
            this.paper.on('blank:pointerdown', () => {
                this.panActive = true;
                this.$scope.$evalAsync();
            });
            this.paper.on('blank:pointerup', () => {
                this.panActive = false;
                delete this.lastMousePos;
                this.$scope.$evalAsync();
            });
            this.paper.$el.on('wheel', this.onMouseWheel.bind(this));

            this.onWindowResize = () => {
                let owidth = this.$element[0].offsetWidth;
                let oheight = this.$element[0].offsetHeight;
                this.paper.setDimensions(
                    owidth, oheight
                );
            };
            this.$rootScope.$on('lab.resize', () => {
                this.$timeout(this.onWindowResize, 100);
            });
            this.$window.addEventListener('resize', this.onWindowResize);
            this.$element.on('mousemove', this.onMouseMove.bind(this));
        }

        if (this.shapes) {
            let padding = this.cellDimensions.width * this.nodeSeparationFactor;
            this.shapes.forEach(s => this.graph.addCell(s));
            joint.layout.DirectedGraph.layout(this.graph, {
                setLinkVertices: false,
                rankDir: 'LR',
                nodeSep: padding,
                rankSep: padding * 2,
                marginX: padding,
                marginY: padding
            });
            this.applyPositionOverrides(this.graph);
            this.scaleToContent();
        }
    }

    applyPositionOverrides(graph) {
        // eslint-disable-next-line
        Object.keys(graph._nodes)
            .map((modelid) => this.paper.getModelById(modelid))
            .forEach((model) => {
                let metadata = model.attributes.metadata;
                if (metadata && metadata.positionOverride) {
                    model.position(
                        model.attributes.metadata.positionOverride.x,
                        model.attributes.metadata.positionOverride.y
                    );
                }
            });
        this.nodePositionsSet = true;
    }

    scaleToContent() {
        this.paper.translate(0, 0);
        this.paper.scale(1);

        let preZoomBBox = this.paper.getContentBBox();
        let xratio =
            this.paper.options.width / (preZoomBBox.x * 2 + preZoomBBox.width);
        let yratio =
            this.paper.options.height / (preZoomBBox.y * 2 + preZoomBBox.height);
        let ratio = xratio > yratio ? yratio : xratio;
        this.setZoom(ratio > 1 ? 1 : ratio, {
            x: 0,
            y: 0
        });

        let postZoomBBox = this.paper.getContentBBox();
        let contentWidth = postZoomBBox.x * 2 + postZoomBBox.width;
        let contentHeight = postZoomBBox.y * 2 + postZoomBBox.height;
        let xoffset = this.paper.options.width / 2 - contentWidth / 2;
        let yoffset = this.paper.options.height / 2 - contentHeight / 2;
        this.paper.translate(xoffset, yoffset);
    }

    onMouseWheel(mouseEvent) {
        let localpoint = this.paper.clientToLocalPoint(
            mouseEvent.originalEvent.x, mouseEvent.originalEvent.y
        );

        if (mouseEvent.originalEvent.deltaY < 0) {
            let newZoom = this.scale * (1 + mouseEvent.originalEvent.deltaY * -0.002);
            this.setZoom(newZoom, localpoint);
        } else {
            let newZoom = this.scale / (1 + mouseEvent.originalEvent.deltaY * 0.002);
            this.setZoom(newZoom, localpoint);
        }
    }

    zoomIn(coords) {
        this.setZoom(this.scale * 1.25, coords);
    }

    zoomOut(coords) {
        this.setZoom(this.scale / 1.25, coords);
    }

    setZoom(zoom, coords) {
        let zoomCoords = coords;

        let oldScale = this.scale;
        this.scale = zoom;
        if (zoom > maxZoom) {
            this.scale = maxZoom;
        }
        if (zoom < minZoom) {
            this.scale = minZoom;
        }

        let scaleDelta = this.scale - oldScale;
        let origin = this.paper.options.origin;

        if (!coords) {
            let offset = this.$element.offset();
            let middle = {
                x: this.$element[0].offsetWidth / 2,
                y: this.$element[0].offsetHeight / 2
            };
            zoomCoords = this.paper.clientToLocalPoint(
                middle.x + offset.left, middle.y + offset.top
            );
        }

        let offsetX = -(zoomCoords.x * scaleDelta) + origin.x;
        let offsetY = -(zoomCoords.y * scaleDelta) + origin.y;

        this.paper.scale(this.scale);
        this.paper.translate(offsetX, offsetY);
        this.$scope.$evalAsync();
    }

    onMouseMove(mouseEvent) {
        if (this.panActive) {
            let translate = {
                x: this.lastMousePos ? this.lastMousePos.x - mouseEvent.offsetX : 0,
                y: this.lastMousePos ? this.lastMousePos.y - mouseEvent.offsetY : 0
            };
            this.lastMousePos = {
                x: mouseEvent.offsetX,
                y: mouseEvent.offsetY
            };
            let origin = this.paper.options.origin;
            this.paper.translate(origin.x - translate.x, origin.y - translate.y);
            this.$scope.$evalAsync();
        }
    }

    onShapeMove(nodeId, position) {
        // TODO redux update node position
        let node = getNodeDefinition(this.reduxState, {nodeId});
        let updatedNode = Object.assign(
            {},
            node,
            {
                metadata: Object.assign(
                    {},
                    node.metadata,
                    {positionOverride: position}
                )
            }
        );
        this.debouncedUpdateNode({payload: updatedNode});
    }

    onPreviewClick() {
        if (this.selectingNode === 'preview') {
            this.cancelNodeSelect();
        } else {
            this.startNodePreview();
        }
    }

    onCompareClick() {
        if (['compare', 'select'].includes(this.selectingNode)) {
            this.cancelNodeSelect();
        } else {
            this.startNodeCompare();
        }
    }

    canUseAction(action) {
        switch (action) {
        case 'preview':
            return !this.selectingNode || this.selectingNode === 'preview';
        case 'compare':
        case 'select':
            return !this.selectingNode ||
                this.selectingNode === 'compare' ||
                this.selectingNode === 'select';
        default:
            return false;
        }
    }
}

const DiagramContainerModule = angular.module('components.lab.diagramContainer', []);

DiagramContainerModule.component('rfDiagramContainer', DiagramContainerComponent);
DiagramContainerModule.controller('DiagramContainerController', DiagramContainerController);

export default DiagramContainerModule;

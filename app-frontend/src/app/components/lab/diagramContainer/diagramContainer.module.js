/* global joint */
import angular from 'angular';
import _ from 'lodash';
import {Map} from 'immutable';
import diagramContainerTpl from './diagramContainer.html';

import WorkspaceActions from '_redux/actions/workspace-actions';
import NodeActions from '_redux/actions/node-actions';
import { nodesFromAnalysis, astFromAnalysisNodes, getNodeDefinition } from '_redux/node-utils';

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

        const unsubscribe = $ngRedux.connect(
            this.mapStateToThis,
            Object.assign({}, WorkspaceActions, NodeActions)
        )(this);
        $scope.$on('$destroy', unsubscribe);

        this.debouncedUpdateNode = _.debounce(this.updateNode, 300);
    }

    $postLink() {
        this.$scope.$watch('$ctrl.workspace', (workspace) => {
            // check if analyses have been added or deleted
            if (workspace && this.analyses) {
                const newAnalyses = workspace.analyses;
                const comparator = (a1, a2) => a1.id === a2.id;
                const added = _.differenceWith(newAnalyses, this.analyses, comparator);
                const removed = _.differenceWith(this.analyses, newAnalyses, comparator);

                if (removed.length) {
                    this.removeAnalyses(removed);
                }

                if (added.length) {
                    this.addAnalyses(added);
                }
            }
        });
    }

    mapStateToThis(state) {
        return {
            workspace: state.lab.workspace,
            extractNodes: state.lab.nodes,
            readonly: state.lab.readonly,
            controls: state.lab.controls,
            selectingNode: state.lab.selectingNode,
            selectedNode: state.lab.selectedNode,
            createNodeSelection: state.lab.createNodeSelection,
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

        const workspaceWatch = this.$scope.$watch('$ctrl.workspace', (workspace) => {
            if (workspace && !this.shapes) {
                this.initDiagram();
            } else {
                workspaceWatch();
            }
        });
    }

    $onDestroy() {
        if (this.onWindowResize) {
            this.$window.removeEventListener('resize', this.onWindowResize);
        }
    }

    initDiagram() {
        const extract = this.workspace.analyses.map((analysis) => {
            return this.labUtils.extractShapes(
                analysis,
                this.cellDimensions
            );
        }).reduce((a, b) => ({
            shapes: a.shapes.concat(b.shapes),
            nodes: a.nodes.merge(b.nodes)
        }), {shapes: [], nodes: new Map()});

        this.analyses = this.workspace.analyses.slice();
        const labNodes = this.workspace.analyses.map((analysis) => {
            return nodesFromAnalysis(analysis);
        }).reduce((a, b) => a.merge(b), new Map());

        this.setNodes(labNodes);

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
            this.graph.on('remove', (cell) => this.onCellDelete(cell));
        } else {
            this.graph.clear();
        }

        if (!this.paper) {
            const el = $(this.$element);
            const height = el.height();
            if (height === 0) {
                const resetDimensions = () => {
                    const elHeight = el.height();
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
            this.paper.on('blank:pointerdown', (event) => {
                this.onMouseClick(event);
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
                const owidth = this.$element[0].offsetWidth;
                const oheight = this.$element[0].offsetHeight;
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
            const padding = this.cellDimensions.width * this.nodeSeparationFactor;
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

    addAnalyses(analyses) {
        if (!analyses.length) {
            throw new Error('called addAnalyses without analyses');
        }

        const placedAnalyses = analyses.map((a) => {
            if (a.addLocation) {
                return this.setRelativePositions(a);
            }
            return a;
        });

        // TODO remove any added nodes that are already on the graph
        const newNodes = placedAnalyses.map((analysis) => {
            return nodesFromAnalysis(analysis);
        }).reduce((a, b) => a.merge(b), new Map());
        this.setNodes(newNodes.merge(this.extractNodes));

        const extract = placedAnalyses.map((analysis) => {
            return this.labUtils.extractShapes(
                analysis,
                this.cellDimensions
            );
        }).reduce((a, b) => ({
            shapes: a.shapes.concat(b.shapes),
            nodes: a.nodes.merge(b.nodes)
        }), {shapes: [], nodes: new Map()});

        this.nodes = extract.nodes.merge(this.nodes);

        const shapes = extract.shapes;
        shapes.forEach((shape) => {
            shape.on('change:position', (node, position) => {
                if (this.nodePositionsSet) {
                    this.onShapeMove(shape.id, position);
                }
            });
            this.graph.addCell(shape);
        });

        this.shapes = shapes.concat(this.shapes);
        this.applyPositionOverrides(this.graph);
        this.scaleToContent();

        this.analyses = this.analyses.concat(placedAnalyses);
    }

    setRelativePositions(analysis) {
        const setOrigin = analysis.addLocation;

        const layoutGraph = new joint.dia.Graph();

        const extract = this.labUtils.extractShapes(
            analysis,
            this.cellDimensions
        );

        const shapes = extract.shapes;
        shapes.forEach(s => layoutGraph.addCell(s));

        const padding = this.cellDimensions.width * this.nodeSeparationFactor;

        joint.layout.DirectedGraph.layout(layoutGraph, {
            setLinkVertices: false,
            rankDir: 'LR',
            nodeSep: padding,
            rankSep: padding * 2
        });

        let nodePositions = new Map();
        shapes.forEach(shape => {
            if (shape.attributes.position) {
                const position = {
                    x: shape.attributes.position.x + setOrigin.x,
                    y: shape.attributes.position.y + setOrigin.y
                };
                nodePositions = nodePositions.set(
                    shape.attributes.id, position
                );
            }
        });

        const nodes = nodesFromAnalysis(analysis).map(
            node => _.merge(
                node, {
                    metadata: { positionOverride: nodePositions.get(node.id) }
                }
            )
        );

        return astFromAnalysisNodes(analysis, nodes);
    }

    removeAnalyses() {
        throw new Error('Removing analyses is not yet supported');
        // removeAnalyses(analyses) {
        // analyses.forEach((analysis) => {
        //     const analysisId = analysis.id;
        //     let nodesToRemove = this.nodes.filter((node) => node.analysisId === analysisId);
        //     let linksToRemove = [];
        //     nodesToRemove.forEach((node) => {
        //         this.graph.getCell(node.id);
        //     });
        // });
        // get all analysis nodes
        // get cell, t
    }

    onCellDelete(cell) {
        const isLink = cell.attributes.type === 'link';

        if (isLink) {
            const childNodeId = cell.attributes.source.id;
            const parentNodeId = cell.attributes.target.id;
            this.splitAnalysis(childNodeId, parentNodeId);
        } else {
            throw new Error('Deleted cell that wasn\'t a link. This shouldn\'t be possible');
        }
    }

    applyPositionOverrides(graph) {
        // eslint-disable-next-line
        Object.keys(graph._nodes)
            .map((modelid) => this.paper.getModelById(modelid))
            .forEach((model) => {
                const metadata = model.attributes.metadata;
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

        const preZoomBBox = this.paper.getContentBBox();
        const xratio =
            this.paper.options.width / (preZoomBBox.x * 2 + preZoomBBox.width);
        const yratio =
            this.paper.options.height / (preZoomBBox.y * 2 + preZoomBBox.height);
        const ratio = xratio > yratio ? yratio : xratio;
        this.setZoom(ratio > 1 ? 1 : ratio, {
            x: 0,
            y: 0
        });

        const postZoomBBox = this.paper.getContentBBox();
        const contentWidth = postZoomBBox.x * 2 + postZoomBBox.width;
        const contentHeight = postZoomBBox.y * 2 + postZoomBBox.height;
        const xoffset = this.paper.options.width / 2 - contentWidth / 2;
        const yoffset = this.paper.options.height / 2 - contentHeight / 2;
        this.paper.translate(xoffset, yoffset);
    }

    onMouseWheel(mouseEvent) {
        const localpoint = this.paper.clientToLocalPoint(
            mouseEvent.originalEvent.x, mouseEvent.originalEvent.y
        );

        if (mouseEvent.originalEvent.deltaY < 0) {
            const newZoom = this.scale * (1 + mouseEvent.originalEvent.deltaY * -0.002);
            this.setZoom(newZoom, localpoint);
        } else {
            const newZoom = this.scale / (1 + mouseEvent.originalEvent.deltaY * 0.002);
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

        const oldScale = this.scale;
        this.scale = zoom;
        if (zoom > maxZoom) {
            this.scale = maxZoom;
        }
        if (zoom < minZoom) {
            this.scale = minZoom;
        }

        const scaleDelta = this.scale - oldScale;
        const origin = this.paper.options.origin;

        if (!coords) {
            const offset = this.$element.offset();
            const middle = {
                x: this.$element[0].offsetWidth / 2,
                y: this.$element[0].offsetHeight / 2
            };
            zoomCoords = this.paper.clientToLocalPoint(
                middle.x + offset.left, middle.y + offset.top
            );
        }

        const offsetX = -(zoomCoords.x * scaleDelta) + origin.x;
        const offsetY = -(zoomCoords.y * scaleDelta) + origin.y;

        this.paper.scale(this.scale);
        this.paper.translate(offsetX, offsetY);
        this.$scope.$evalAsync();
    }

    onMouseMove(mouseEvent) {
        if (this.panActive) {
            const translate = {
                x: this.lastMousePos ? this.lastMousePos.x - mouseEvent.offsetX : 0,
                y: this.lastMousePos ? this.lastMousePos.y - mouseEvent.offsetY : 0
            };
            this.lastMousePos = {
                x: mouseEvent.offsetX,
                y: mouseEvent.offsetY
            };
            const origin = this.paper.options.origin;
            this.paper.translate(origin.x - translate.x, origin.y - translate.y);
            this.$scope.$evalAsync();
        }
    }

    onShapeMove(nodeId, position) {
        // TODO redux update node position
        const node = getNodeDefinition(this.reduxState, {nodeId});
        const updatedNode = Object.assign(
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

    onMouseClick(event) {
        if (this.createNodeSelection) {
            const origin = this.paper.options.origin;
            const clickLocation = {
                x: (event.offsetX - origin.x) / this.scale,
                y: (event.offsetY - origin.y) / this.scale
            };
            this.finishCreatingNode(clickLocation);
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

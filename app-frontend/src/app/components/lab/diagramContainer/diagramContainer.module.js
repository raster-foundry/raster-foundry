/* global joint */
import angular from 'angular';
import _ from 'lodash';
import {Map, Set} from 'immutable';
import diff from 'immutablediff';
import diagramContainerTpl from './diagramContainer.html';

import WorkspaceActions from '_redux/actions/workspace-actions';
import NodeActions from '_redux/actions/node-actions';
import NodeUtils from '_redux/node-utils';

const DiagramContainerComponent = {
    templateUrl: diagramContainerTpl,
    controller: 'DiagramContainerController'
};

const maxZoom = 3;
const minZoom = 0.025;

class DiagramContainerController {
    constructor( // eslint-disable-line max-params
        $element, $scope, $state, $timeout, $compile, $document, $window, $rootScope,
        mousetipService, labUtils, $ngRedux, $log
    ) {
        'ngInject';
        this.$log = $log;
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

    mapStateToThis(state) {
        return {
            workspace: state.lab.workspace,
            extractNodes: state.lab.nodes,
            readonly: state.lab.readonly,
            controls: state.lab.controls,
            selectingNode: state.lab.selectingNode,
            selectedNode: state.lab.selectedNode,
            createNodeSelection: state.lab.createNodeSelection,
            reduxState: state,
            nodes: state.lab.nodes,
            links: state.lab.links,
            linksBySource: state.lab.linksBySource,
            linksByTarget: state.lab.linksByTarget,
            cellDimensions: state.lab.cellDimensions,
            nodeSeparationFactor: state.lab.nodeSeparationFactor
        };
    }

    $onInit() {
        this.scale = 1;

        this.$scope.$on('$destroy', this.$onDestroy.bind(this));

        this.workspaceElement = this.$element[0].children[0];
        this.comparison = [false, false];
        this.panActive = false;
        this.ignorePositionChanges = true;
    }

    $postLink() {
        const workspaceWatch = this.$scope.$watch('$ctrl.workspace', (workspace) => {
            if (workspace && !this.diagramInitialized) {
                this.initDiagram();
                this.$scope.$watch('$ctrl.nodes', this.onNodeUpdate.bind(this));
                this.$scope.$watch('$ctrl.linksByTarget', this.onLinksByTargetUpdate.bind(this));
                workspaceWatch();
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
        const {nodes, linksBySource, linksByTarget} =
            NodeUtils.analysesToNodeLinks(this.workspace.analyses, this.cellDimensions);

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

        this.setDiagram(
            nodes, linksBySource, linksByTarget, this.graph, this.onShapeMove.bind(this)
        );
        this.diagramInitialized = true;
    }

    applyCellPositions() {
        this.ignorePositionChanges = true;
        // const padding = this.cellDimensions.width * this.nodeSeparationFactor;
        // joint.layout.DirectedGraph.layout(this.graph, {
        //     setLinkVertices: false,
        //     rankDir: 'LR',
        //     nodeSep: padding,
        //     rankSep: padding * 2,
        //     marginX: padding,
        //     marginY: padding
        // });
        this.applyPositionOverrides(this.graph);
        this.ignorePositionChanges = false;
    }

    onNodeUpdate(newNodes, oldNodes) {
        let nodeDiff;
        if (!this.nodesInitialized) {
            nodeDiff = diff(new Map(), newNodes);
        } else {
            nodeDiff = diff(oldNodes, newNodes);
        }
        let nodesInGraph = new Set(this.nodesInitialized ? oldNodes.keys() : []);
        let nodesAddedOrDeleted = false;
        nodeDiff.forEach((change) => {
            const op = change.get('op');
            const nodeId = _.last(change.get('path').split('/'));

            switch (op) {
            case 'add':
                // add node to diagram
                const node = newNodes.get(nodeId);
                node.shape.on('change:position', (shape, position) => {
                    if (!this.ignorePositionChanges) {
                        this.onShapeMove(node.id, position);
                    }
                });

                this.graph.addCell(node.shape);
                node.inGraph = true;
                const targetLinks = this.linksBySource.get(nodeId) || new Map();
                const sourceLinks = this.linksByTarget.get(nodeId) || new Map();

                const validTargetLinks = targetLinks.filter(
                    (link, targetId) => !link.inGraph && nodesInGraph.has(targetId)
                ).valueSeq();
                const validSourceLinks = sourceLinks.filter(
                    (link, sourceId) => !link.inGraph && nodesInGraph.has(sourceId)
                ).valueSeq();

                const validLinks = validTargetLinks.concat(validSourceLinks);
                validLinks.forEach((link) => {
                    link.inGraph = true;
                    if (!link.shape) {
                        throw new Error('Tried to add link without shape to graph', link);
                    }
                    this.graph.addCell(link.shape);
                });

                nodesInGraph = nodesInGraph.add(nodeId);
                nodesAddedOrDeleted = true;
                break;
            case 'delete':
                this.$log.log('Deleting node', nodeId, node);
                // remove node from diagram
                // this should happen after all the other stuff (deleting links, api updated) does
                break;
            case 'replace':
                this.$log.log('Ignoring node update', nodeId, change.toString());
                // don't really care? node itself should manage
                break;
            default:
                this.$log.log('Unsupported node change op was:', op, nodeId, change);
            }
        });

        // links should be removed before the nodes are
        // if links for nodes that are being added, add the nodes then add the links
        // if a deleted node is a root node, check if any of its children qualify as new
        // root nodes. If so, create a new analysis for each new root node

        if (this.diagramInitialized && nodesAddedOrDeleted) {
            this.applyCellPositions();
        }
        if (this.diagramInitialized && !this.initialScale && this.nodes.size) {
            this.scaleToContent();
            this.initialScale = true;
        }
        if (!this.nodesInitialized) {
            this.nodesInitialized = true;
        }
        this.paper.scale(this.scale);
    }

    // both maps change simultaneously, so don't need to listen to both
    onLinksByTargetUpdate(newLinks, oldLinks) {
        const linkDiff = diff(oldLinks, newLinks);
        linkDiff.map((change) => {
            const op = change.get('op');
            const path = change.get('path').split('/').slice(1);
            switch (op) {
            case 'add':
                if (path.length === 1) {
                    const sourceLinkMap = newLinks.get(_.first(path));
                    // filter out links which are added by the nodes
                    const linksNotInGraph = sourceLinkMap.filter(
                        (link) => !link.inGraph
                    );
                    // filter out links which have not had their nodes added yet.
                    // nodes will add them later.
                    const linksWithAddedNodes = linksNotInGraph.filter((link) => {
                        return this.nodes.get(link.source).inGraph &&
                            this.nodes.get(link.target).inGraph;
                    });
                    linksWithAddedNodes.forEach((link) => {
                        this.graph.addCell(link.shape);
                        link.inGraph = true;
                        this.$log.log('link added to graph in update');
                    });
                } else if (path.length === 2) {
                    // single link added
                    const link = newLinks.get(path[0]).get(path[1]);
                    if (!link.inGraph &&
                        this.nodes.get(link.source).inGraph &&
                        this.nodes.get(link.target).inGraph
                       ) {
                        this.graph.addCell(link.shape);
                        link.inGraph = true;
                        this.$log.log('link added to graph in update');
                    }
                } else {
                    throw new Error('Link path in diff makes no sense:', change);
                }
                break;
            case 'delete':
                if (path.length === 1) {
                    // check the number of links deleted
                } else if (path.length === 2) {
                    // single link deleted
                } else {
                    throw new Error('Link path in diff makes no sense:', change);
                }
                break;
            default:
            }
        });
    }


    onCellDelete(cell) {
        const isLink = cell.attributes.type === 'link';

        if (isLink) {
            const sourceNodeId = cell.attributes.source.id;
            const targetNodeId = cell.attributes.target.id;
            const targetLinks = this.linksByTarget.get(targetNodeId);
            if (!targetLinks) {
                throw new Error(`Tried to delete missing link: ${sourceNodeId} -> ${targetNodeId}`);
            }
            const link = targetLinks.get(sourceNodeId);
            if (!link) {
                throw new Error(`Tried to delete missing link: ${sourceNodeId} -> ${targetNodeId}`);
            }
            this.deleteLink(link);
        } else {
            throw new Error('Deleted cell that wasn\'t a link.', cell);
        }
    }

    applyPositionOverrides(graph) {
        const wasIgnoringPositionChanges = this.ignorePositionChanges;
        this.ignorePositionChanges = true;
        // eslint-disable-next-line
        Object.keys(graph._nodes)
            .map((modelid) => this.paper.getModelById(modelid))
            .forEach((model) => {
                const node = this.nodes.get(model.id);
                const metadata = node.metadata;
                if (metadata && metadata.positionOverride) {
                    model.position(
                        metadata.positionOverride.x,
                        metadata.positionOverride.y
                    );
                }
            });
        this.ignorePositionChanges = wasIgnoringPositionChanges;
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
        const node = this.nodes.get(nodeId);
        const updatedNode = _.set(node, ['metadata', 'positionOverride'], position);
        this.debouncedUpdateNode(updatedNode);
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

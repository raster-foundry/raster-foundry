const Map = require('es6-map');
/* global joint, $ */

import { coreTools, compressedTool } from './toolJson.js';


export default class DiagramContainerController {
    constructor( // eslint-disable-line max-params
        $element, $scope, $state, $timeout, $compile, mousetipService) {
        'ngInject';
        this.$element = $element;
        this.$scope = $scope;
        this.$state = $state;
        this.$timeout = $timeout;
        this.$compile = $compile;
        this.mousetipService = mousetipService;
    }

    $onInit() {
        this.workspaceElement = this.$element[0].children[0];
        this.comparison = [false, false];
        this.cellSize = [300, 75];
        this.paddingFactor = 0.8;
        this.nodeSeparationFactor = 0.25;
        this.initContextMenus();
        this.contextMenuTpl =
            `<div class="lab-contextmenu" ng-show="isShowingContextMenu">
                <div class="btn-group">
                    <button ng-repeat="item in currentContextMenu"
                        ng-click="item.callback()"
                        class="btn btn-default">
                        {{item.label}}
                    </button>
                </div>
            </div>`;

        this.toolJson = compressedTool;
        this.coreToolsJson = coreTools;
        this.extractInputs();
        this.extractShapes();
        this.initDiagram();
    }

    getToolLabel(json) {
        return json.label ||
            (this.coreToolsJson[json.apply] ?
            this.coreToolsJson[json.apply].label : json.apply);
    }

    $onChanges(changes) {
        if (this.graph && (changes.shapes || changes.cellLabel)) {
            this.graph.clear();
            this.initShapes();
            this.shapes.forEach(s => this.graph.addCell(s));
            this.initDiagram();
        }
    }

    initDiagram() {
        if (!this.graph) {
            this.graph = new joint.dia.Graph();
        } else {
            this.graph.clear();
        }

        if (!this.paper) {
            this.paper = new joint.dia.Paper({
                el: this.workspaceElement,
                height: $(this.workspaceElement).height(),
                width: $(this.workspaceElement).width(),
                gridSize: 25,
                drawGrid: true,
                model: this.graph,
                clickThreshold: 4
            });
            this.paper.drawGrid({
                color: '#aaa',
                thickness: 1
            });
            this.paper.on('blank:pointerclick', this.onPaperClick.bind(this));
            this.paper.on('cell:pointerclick', this.onCellClick.bind(this));
        }

        if (this.shapes) {
            let padding = this.cellSize[0] * this.nodeSeparationFactor;
            this.shapes.forEach(s => this.graph.addCell(s));
            joint.layout.DirectedGraph.layout(this.graph, {
                setLinkVertices: false,
                rankDir: 'LR',
                nodeSep: padding,
                rankSep: padding * 2,
                marginX: padding,
                marginY: padding
            });
            this.paper.scaleContentToFit({
                padding: padding
            });
        }
    }

    initContextMenus() {
        this.contextMenus = new Map();
        this.defaultContextMenu = [{
            label: 'Compare to...',
            callback: () => {
                this.startComparison();
            }
        }, {
            label: 'View output',
            callback: () => {
                this.onPreview({data: this.nodes.get(this.selectedCellView.model.id)});
            }
        }];
        this.cancelComparisonMenu = [{
            label: 'Cancel',
            callback: () => {
                this.cancelComparison();
            }
        }];
    }

    onPaperClick() {
        this.$scope.$evalAsync(() => {
            if (this.isComparing) {
                this.cancelComparison();
            } else {
                this.hideContextMenu();
                this.unselectCellView();
            }
        });
    }

    onCellClick(cv, evt) {
        this.$scope.$evalAsync(() => {
            if (this.isComparing) {
                this.continueComparison(cv);
            } else {
                this.selectCellView(cv, evt);
            }
        });
    }

    extractInputs() {
        this.inputsJson = [];
        this.doExtractInput(this.toolJson);
    }

    doExtractInput(json, parentTool) {
        if (json.hasOwnProperty('args')) {
            let args = json.args;
            let tool = this.getToolLabel(json);
            if (!Array.isArray(args)) {
                args = Object.values(args);
            }
            args.forEach(j => this.doExtractInput(j, tool));
        } else {
            this.inputsJson.push(Object.assign(json, {parent: parentTool}));
        }
    }

    extractShapes() {
        this.shapes = [];
        this.nodes = new Map();
        this.doExtractShapes(this.toolJson);
    }

    doExtractShapes(json) {
        const isLeaf = !json.hasOwnProperty('args');
        let children = false;
        let args = [];

        if (!isLeaf) {
            args = json.args;
            if (!Array.isArray(args)) {
                args = Object.values(args);
            }
            children = args
                .map(j => this.doExtractShapes(j))
                .filter(c => Boolean(c));
        }

        if (isLeaf && json.type === 'layer' || !isLeaf) {
            let inputCount = args
                .filter(a => a.type === 'layer' || a.apply)
                .length;
            return this.createRectangle({
                label: this.getToolLabel(json),
                inputs: inputCount,
                outputs: ['Output'],
                tag: json.tag,
                children: children
            });
        }
        return false;
    }

    startComparison() {
        this.mousetipService.set('Select a node to compare');
        this.isComparing = true;
        this.comparison[0] = this.nodes.get(this.selectedCellView.model.id);
        this.showContextMenu(this.selectedCellView, this.cancelComparisonMenu);
    }

    continueComparison(cv) {
        this.mousetipService.remove();
        this.hideContextMenu();
        this.isComparing = false;
        this.comparison[1] = this.nodes.get(cv.model.id);
        this.onPreview({data: this.comparison});
    }

    cancelComparison() {
        this.hideContextMenu();
        this.isComparing = false;
        this.mousetipService.remove();
    }

    showContextMenu(cv, contextMenu) {
        this.hideContextMenu();

        let bounds = cv.getBBox() || this.selectedCellView.getBBox || false;
        let menuScope = this.$scope.$new();
        menuScope.currentContextMenu = contextMenu ||
                                       this.contextMenus.get(cv.model.id) ||
                                       this.defaultContextMenu;

        this.contextMenuEl = this.$compile(this.contextMenuTpl)(menuScope)[0];
        this.$element[0].appendChild(this.contextMenuEl);
        this.contextMenuEl = $(this.contextMenuEl).css({
            top: bounds.y,
            left: bounds.x + bounds.width / 2
        });

        menuScope.$evalAsync(() => {
            menuScope.isShowingContextMenu = true;
        });
    }

    hideContextMenu() {
        this.isShowingContextMenu = false;
        if (this.contextMenuEl) {
            this.contextMenuEl.remove();
        }
    }

    selectCellView(cellView) {
        this.unselectCellView();
        this.selectedCellView = cellView;
        cellView.model.attr({
            rect: {
                stroke: '#353b59',
                'stroke-width': '1'
            }
        });
        this.showContextMenu(cellView);
    }

    unselectCellView() {
        if (this.selectedCellView) {
            this.selectedCellView.model.attr({
                rect: {
                    stroke: '#959cad',
                    'stroke-width': 0.5
                }
            });
            this.selectedCellView = null;
        }
    }

    createRectangle(config) {
        // We're manually creating node ids, which will be dictated by order in `shapes`
        let id = `node-${this.shapes.length}`;

        let label = joint.util.breakText(config.label || id, {
            width: this.cellSize[0] * this.paddingFactor,
            height: this.cellSize[1] * this.paddingFactor
        });

        let inputs = this.createPorts(config.inputs, config.outputs);

        let shape = new joint.shapes.basic.Rect({
            id: id,
            size: {
                width: this.cellSize[0],
                height: this.cellSize[1]
            },
            attrs: {
                rect: {
                    fill: '#fff',
                    stroke: '#959cad',
                    'stroke-width': 0.5,
                    rx: 2,
                    ry: 4
                },
                text: {
                    fill: '#353b59',
                    text: label
                }
            },
            ports: {
                groups: {
                    inputs: {
                        position: {
                            name: 'left'
                        }
                    },
                    outputs: {
                        position: {
                            name: 'right'
                        }
                    }
                },
                items: inputs
            }
        });

        this.shapes.push(shape);
        this.nodes.set(id, config);

        if (config.children) {
            let inputPorts = inputs.filter(i => i.group === 'inputs');
            config.children.forEach((c, idx) => {
                this.createLink([c.id, 'Output'], [shape.id, inputPorts[idx].id]);
            });
        }
        return shape;
    }

    createPorts(inputs, outputs) {
        let ports = [];
        let inputList = Array.isArray(inputs) ?
            inputs : Array(inputs).fill();

        ports = inputList.map((_, idx) => {
            return {
                id: `input-${idx}`,
                label: `input-${idx}`,
                group: 'inputs'
            };
        });

        ports = ports.concat(outputs.map(o => {
            return {
                id: o,
                group: 'outputs'
            };
        }));

        return ports;
    }

    createLink(src, target) {
        let link = new joint.dia.Link({
            source: {id: src[0], port: src[1]},
            target: {id: target[0], port: target[1]},
            attrs: {
                '.marker-target': {
                    d: 'M 4 0 L 0 2 L 4 4 z'
                }
            }
        });
        this.shapes.push(link);
        return link;
    }
}

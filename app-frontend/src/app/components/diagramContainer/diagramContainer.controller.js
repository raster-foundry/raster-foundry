const Map = require('es6-map');
/* global joint, $ */

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
        this.initShapes();
        this.initDiagram();
        this.contextMenuTpl =
            `<div class="lab-contextmenu" ng-show="isShowingContextMenu">
                <div>
                    <ul>
                        <li ng-repeat="item in currentContextMenu"
                            ng-click="item.callback()">
                            {{item.label}}
                        </li>
                    </ul>
                </div>
            </div>`;
    }

    $onChanges(changes) {
        if (this.graph && (changes.shapes || changes.cellLabel)) {
            this.graph.clear();
            this.initShapes();
            this.shapes.forEach(s => this.graph.addCell(s));
            this.initDiagram();
        }
    }

    initShapes() {
        // @TODO: this process is not very elegant, could be better handled
        this.shapes = [];
        this.nodes = new Map();

        let ndviBefore = this.createRectangle({
            label: 'NDVI - Before',
            inputs: ['Red', 'NIR'],
            outputs: ['Output']
        });

        this.nodes.set(ndviBefore, {
            input: 0,
            name: 'NDVI - Before'
        });

        let reclassifyBefore = this.createRectangle({
            label: 'Reclassify - Before',
            inputs: ['Input'],
            outputs: ['Output']
        });

        this.nodes.set(reclassifyBefore, {
            input: 0,
            name: 'Reclassify - Before'
        });

        this.createLink([ndviBefore, 'Output'], [reclassifyBefore, 'Input']);

        let ndviAfter = this.createRectangle({
            label: 'NDVI - After',
            inputs: ['Red', 'NIR'],
            outputs: ['Output']
        });

        this.nodes.set(ndviAfter, {
            input: 1,
            name: 'NDVI - After'
        });

        let reclassifyAfter = this.createRectangle({
            label: 'Reclassify - After',
            inputs: ['Input'],
            outputs: ['Output']
        });

        this.nodes.set(reclassifyAfter, {
            input: 1,
            name: 'Reclassify - After'
        });

        this.createLink([ndviAfter, 'Output'], [reclassifyAfter, 'Input']);

        let subtract = this.createRectangle({
            label: 'Subtract',
            inputs: ['First', 'Second'],
            outputs: ['Output']
        });

        this.nodes.set(subtract, {
            input: 1,
            name: 'Subtract'
        });

        this.createLink([reclassifyBefore, 'Output'], [subtract, 'First']);
        this.createLink([reclassifyAfter, 'Output'], [subtract, 'Second']);
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
        this.$element[0].append(this.contextMenuEl);
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
                stroke: '#465076',
                'stroke-width': '2'
            }
        });
        this.showContextMenu(cellView);
    }

    unselectCellView() {
        if (this.selectedCellView) {
            this.selectedCellView.model.attr({
                rect: {
                    stroke: '#999999',
                    'stroke-width': 1
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

        let shape = new joint.shapes.basic.Rect({
            id: id,
            size: {
                width: this.cellSize[0],
                height: this.cellSize[1]
            },
            attrs: {
                rect: {
                    fill: '#f8f9fa',
                    stroke: '#999999',
                    'stroke-width': 1
                },
                text: {
                    fill: '#333333',
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
                items: this.createPorts(config.inputs, config.outputs)
            }
        });

        this.shapes.push(shape);
        return id;
    }

    createPorts(inputs, outputs) {
        let ports = [];
        inputs.forEach(i => {
            ports.push({
                id: i,
                group: 'inputs'
            });
        });
        outputs.forEach(o => {
            ports.push({
                id: o,
                group: 'outputs'
            });
        });

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

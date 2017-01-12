const Map = require('es6-map');
/* global joint, $ */

export default class DiagramContainerController {
    constructor($element, $scope, $state, $timeout) {
        'ngInject';
        this.$element = $element;
        this.$scope = $scope;
        this.$state = $state;
        this.$timeout = $timeout;
    }

    $onInit() {
        this.workspaceElement = this.$element[0].children[0];
        this.contextMenuElement = this.$element[0].children[1];
        this.contextMenuMeasureElement = this.$element[0].children[2];
        this.cellSize = [300, 75];
        this.paddingFactor = 0.8;
        this.nodeSeparationFactor = 0.25;
        this.initContextMenus();
        this.initShapes();
        this.initDiagram();
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
        this.shapes = [];

        this.createRectangle('NDVI 1', {
            label: 'NDVI - Before',
            inputs: ['Red', 'NIR'],
            outputs: ['Output']
        });

        this.contextMenus.set('NDVI 1', [{
            label: 'Compare NDVI-1 to...',
            callback: () => {
                // @TODO: add compare tool
            }
        }, {
            label: 'View output',
            callback: () => {
                // @TODO: show map with actively selected cell
            }
        }]);

        this.createRectangle('Reclassify 1', {
            label: 'Reclassify',
            inputs: ['Input'],
            outputs: ['Output']
        });

        this.createLink(['NDVI 1', 'Output'], ['Reclassify 1', 'Input']);

        this.createRectangle('NDVI 2', {
            label: 'NDVI - After',
            inputs: ['Red', 'NIR'],
            outputs: ['Output']
        });

        this.createRectangle('Reclassify 2', {
            label: 'Reclassify',
            inputs: ['Input'],
            outputs: ['Output']
        });

        this.createLink(['NDVI 2', 'Output'], ['Reclassify 2', 'Input']);

        this.createRectangle('Subtract', {
            label: 'Subtract',
            inputs: ['First', 'Second'],
            outputs: ['Output']
        });

        this.createLink(['Reclassify 1', 'Output'], ['Subtract', 'First']);
        this.createLink(['Reclassify 2', 'Output'], ['Subtract', 'Second']);
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
                model: this.graph
            });
            this.paper.drawGrid({
                color: '#aaa',
                thickness: 1
            });
            this.paper.on('blank:pointerclick', () => {
                this.hideContextMenu();
                this.unselectCellView();
            });
            this.paper.on('cell:pointerclick', (cv, evt) => {
                this.selectCellView(cv, evt);
            });
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
            // @TODO: figure out centering
        }
    }

    initContextMenus() {
        this.contextMenus = new Map();
        this.defaultContextMenu = [{
            label: 'Compare to...',
            callback: () => {
                // @TODO: add compare tool
            }
        }, {
            label: 'View output',
            callback: () => {
                // @TODO: show map with actively selected cell
            }
        }];
    }

    showContextMenu(cv, evt) {
        let bounds = cv.getBBox();

        this.$scope.$evalAsync(() => {
            if (this.contextMenus.has(cv.model.id)) {
                this.currentContextMenu = this.contextMenus.get(cv.model.id);
            } else {
                this.currentContextMenu = this.defaultContextMenu;
            }
        });

        this.$timeout(() => {
            $(this.contextMenuElement).css({
                top: bounds.y - $(this.contextMenuMeasureElement).height() - 10,
                left:
                    bounds.x +
                    Math.abs($(this.contextMenuMeasureElement).width() - bounds.width) / 2
            });
            this.isShowingContextMenu = true;
        });
    }

    hideContextMenu() {
        this.isShowingContextMenu = false;
        this.$scope.$evalAsync();
    }

    selectCellView(cellView, evt) {
        this.unselectCellView();
        this.selectedCellView = cellView;
        cellView.model.attr({
            rect: {
                stroke: '#465076',
                'stroke-width': '2'
            }
        });
        this.showContextMenu(cellView, evt);
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

    createRectangle(id, config) {
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
        return shape;
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

/* global joint */

export default class DiagramContainerController {
    constructor($element) {
        'ngInject';
        this.$element = $element;
    }

    $onInit() {
        this.workspaceElement = this.$element[0].children[0];
        this.initShapes();
        this.initDiagram();
    }

    $onChanges(changes) {
        if (this.graph && (changes.shapes || changes.cellLabel)) {
            this.graph.clear();
            this.initShapes();
            this.shapes.forEach(s => this.graph.addCell(s));
        }
    }

    initShapes() {
        this.shapes = [];
        this.shapes.push(new joint.shapes.basic.Rect({
            position: {
                x: ($(this.workspaceElement).width() - 225) / 2,
                y: ($(this.workspaceElement).height() - 75) / 2
            },
            size: {
                width: 350,
                height: 75
            },
            attrs: {
                rect: {
                    rx: 4,
                    ry: 7,
                    fill: '#dfdfdf'
                },
                text: {
                    fill: '#333333',
                    text: this.cellLabel
                }
            },
            ports: {
                groups: {
                    inputs: {
                        position: {
                            name: 'top'
                        }
                    },
                    functions: {
                        position: {
                            name: 'left'
                        }
                    }
                },
                items: [
                    {
                        group: 'inputs'
                    },
                    {
                        group: 'functions'
                    }
                ]
            }
        }));
    }

    initDiagram() {
        this.graph = new joint.dia.Graph();
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
            if (this.onPaperClick) {
                this.onPaperClick();
            }
        });
        this.paper.on('cell:pointerclick', () => {
            if (this.onCellClick) {
                this.onCellClick();
            }
        });
        if (this.shapes) {
            this.shapes.forEach(s => this.graph.addCell(s));
        }
    }
}

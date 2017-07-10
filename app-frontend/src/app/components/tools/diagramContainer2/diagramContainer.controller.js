/* global joint, $, _ */

const Map = require('es6-map');


export default class DiagramContainerController {
    constructor( // eslint-disable-line max-params
        $element, $scope, $state, $timeout, $compile, mousetipService, toolService) {
        'ngInject';
        this.$element = $element;
        this.$scope = $scope;
        this.$state = $state;
        this.$timeout = $timeout;
        this.$compile = $compile;
        this.mousetipService = mousetipService;
        this.toolService = toolService;
    }

    $onInit() {
        let $scope = this.$scope;
        let $compile = this.$compile;
        joint.shapes.html = {};
        joint.shapes.html.Element = joint.shapes.basic.Rect.extend({
            defaults: joint.util.deepSupplement({
                type: 'html.Element',
                attrs: {
                    rect: { stroke: 'none', 'fill-opacity': 0}
                }
            }, joint.shapes.basic.Rect.prototype.defaults)
        });

        joint.shapes.html.ElementView = joint.dia.ElementView.extend({
            template: `
                <div class="diagram-cell ">
                  <rf-diagram-node-header
                    data-model="model"
                    data-invalid="model.get('cellType') === 'Input'"
                    data-menu-options="menuOptions"
                    >
                  </rf-diagram-node-header>
                  <div class="node-body"></div>
                </div>
        `,
            initialize: function () {
                _.bindAll(this, 'updateBox');
                joint.dia.ElementView.prototype.initialize.apply(this, arguments);
                this.model.on('change', this.updateBox, this);
                this.$box = angular.element(this.template);
                this.scope = $scope.$new();
                $compile(this.$box)(this.scope);

                this.updateBox();
            },
            render: function () {
                joint.dia.ElementView.prototype.render.apply(this, arguments);
                this.paper.$el.prepend(this.$box);
                this.updateBox();
                return this;
            },
            updateBox: function () {
                let bbox = this.model.getBBox();
                this.scope.model = this.model;

                this.$box.css({
                    width: bbox.width,
                    height: bbox.height,
                    left: bbox.x,
                    top: bbox.y
                });
            },
            removeBox: function () {
                this.$box.remove();
            }
        });

        this.workspaceElement = this.$element[0].children[0];
        this.comparison = [false, false];
        this.cellSize = [300, 150];
        this.paddingFactor = 0.8;
        this.nodeSeparationFactor = 0.25;
        this.panActive = false;
        this.initContextMenus();
        this.extractInputs();
        this.extractShapes();
        this.initDiagram();
    }

    getToolLabel(json) {
        if (json.metadata && json.metadata.label) {
            return json.metadata.label;
        }
        return json.apply;
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
            this.paper.on('blank:pointerdown', () => {
                this.panActive = true;
                this.$scope.$evalAsync();
            });
            this.paper.on('blank:pointerup', () => {
                this.panActive = false;
                this.$scope.$evalAsync();
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
        }
    }

    initContextMenus() {
        this.defaultContextMenu = [{
            label: 'Compare to...',
            callback: ($event, model) => {
                this.selectCell(model);
                this.startComparison(model.get('id'));
            }
        }, {
            label: 'View output',
            callback: ($event, model) => {
                this.onPreview({data: model.get('id')});
            }
        }, {
            type: 'divider'
        }, {
            label: 'Share',
            callback: ($event, model) => {
                this.onShare({data: model.get('id')});
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
            }
            this.unselectCell();
        });
    }

    onCellClick(cv) {
        this.$scope.$evalAsync(() => {
            if (this.isComparing) {
                this.continueComparison(cv);
            }
        });
    }

    extractInputs() {
        this.inputsJson = [];

        let json = Object.assign({}, this.toolDefinition);
        let inputs = [json];
        while (inputs.length) {
            let input = inputs.pop();
            let args = input.args;
            if (args) {
                let tool = this.getToolLabel(input);
                if (!Array.isArray(args)) {
                    args = Object.values(args);
                }
                inputs = inputs.concat(args.map((a) => {
                    return Object.assign({parent: tool}, a);
                }));
            } else {
                this.inputsJson.push(input);
            }
        }
    }

    extractShapes() {
        let nextId = 0;
        let nodes = new Map();
        let shapes = [];
        let json = Object.assign({}, this.toolDefinition);
        let inputs = [json];

        while (inputs.length) {
            let input = inputs.pop();
            let rectangle;
            let args;

            // Args can be array or object, if object, convert to array
            if (input.args) {
                args = Array.isArray(input.args) ? input.args : Object.values(input.args);
            } else {
                args = [];
            }

            // Input nodes not of the layer type are not made into rectangles
            if (!input.type || input.type === 'src' || input.type === 'const') {
                let rectInputs = args.length;
                let rectOutputs = ['Output'];
                let ports = this.createPorts(rectInputs, rectOutputs);
                let currentId = nextId.toString();
                let rectAttrs = {
                    id: input.id,
                    label: this.getToolLabel(input),
                    inputs: rectInputs,
                    outputs: rectOutputs,
                    tag: input.tag,
                    ports: ports
                };

                rectangle = this.constructRect(rectAttrs);

                nodes.set(currentId, rectAttrs);

                shapes.push(rectangle);

                if (input.parent) {
                    let firstPort = input.parent.portData.ports.filter(i => {
                        return i.group === 'inputs' && !i.isConnected;
                    })[0];

                    firstPort.isConnected = true;

                    let link = new joint.dia.Link({
                        source: {id: rectangle.id, port: 'Output'},
                        target: {id: input.parent.id, port: firstPort.id},
                        attrs: {
                            '.marker-target': {
                                d: 'M 4 0 L 0 2 L 4 4 z'
                            }
                        }
                    });

                    shapes.push(link);
                }
                if (args) {
                    inputs = inputs.concat(args.map((a) => {
                        return Object.assign({
                            parent: rectangle
                        }, a);
                    }));
                }
                nextId += 1;
            }
        }
        this.shapes = shapes;
        this.nodes = nodes;
    }

    constructRect(config) {
        return new joint.shapes.html.Element({
            id: config.id,
            size: {
                width: this.cellSize[0],
                height: this.cellSize[1]
            },
            cellType: config.inputs ? 'Function' : 'Input',
            title: config.label || config.id.toString(),
            contextMenu: this.defaultContextMenu,
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
                items: config.ports
            }
        });
    }

    startComparison(id) {
        this.mousetipService.set('Select a node to compare');
        this.isComparing = true;
        this.comparison[0] = id;
    }

    continueComparison(cv) {
        this.mousetipService.remove();
        this.hideContextMenu();
        this.isComparing = false;
        this.comparison[1] = cv.model.id;
        this.onPreview({data: this.comparison});
        this.unselectCell();
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

    selectCell(model) {
        this.unselectCell();
        this.selectedCell = model;
        model.attr({
            rect: {
                stroke: '#738FFC',
                'stroke-width': '1'
            }
        });
    }

    unselectCell() {
        if (this.selectedCell) {
            this.selectedCell.attr({
                rect: {
                    stroke: '#959cad',
                    'stroke-width': 0.5
                }
            });
            this.selectedCell = null;
        }
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
        return link;
    }
}

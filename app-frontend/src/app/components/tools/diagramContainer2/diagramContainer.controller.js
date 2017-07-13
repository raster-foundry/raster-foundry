/* global joint, $, _ */

const Map = require('es6-map');

const maxZoom = 3;
const minZoom = 0.025;

export default class DiagramContainerController {
    constructor( // eslint-disable-line max-params
        $element, $scope, $state, $timeout, $compile, $document, $window,
        mousetipService, toolService
    ) {
        'ngInject';
        this.$element = $element;
        this.$scope = $scope;
        this.$state = $state;
        this.$timeout = $timeout;
        this.$compile = $compile;
        this.$document = $document;
        this.$window = $window;
        this.mousetipService = mousetipService;
        this.toolService = toolService;
    }

    $onInit() {
        let $scope = this.$scope;
        let $compile = this.$compile;

        this.scale = 1;

        $scope.$on('$destroy', this.$onDestroy.bind(this));

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
                <div class="diagram-cell">
                  <rf-diagram-node-header
                    data-model="model"
                    data-invalid="model.get('invalid')"
                    data-menu-options="menuOptions"
                  ></rf-diagram-node-header>
                  <rf-input-node
                    ng-if="model.get('cellType') === 'src'"
                    data-model="model"
                    on-change="onChange({sourceId: sourceId, project: project, band: band})"
                  ></rf-input-node>
                  <rf-operation-node
                    ng-if="model.get('cellType') === 'function'"
                    data-model="model"
                  ></rf-operation-node>
                  <rf-constant-node
                    ng-if="model.get('cellType') === 'const'"
                    data-model="model"
                    on-change="onChange({override: override})"
                  ></rf-constant-node>
                </div>`,
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
                this.listenTo(this.paper, 'translate', () => {
                    let bbox = this.model.getBBox();
                    let origin = this.paper ? this.paper.options.origin : {x: 0, y: 0};
                    this.$box.css({
                        left: bbox.x * this.scale + origin.x,
                        top: bbox.y * this.scale + origin.y
                    });
                });
                this.scale = 1;
                this.listenTo(this.paper, 'scale', (scale) => {
                    this.scale = scale;
                    let bbox = this.model.getBBox();
                    let origin = this.paper ? this.paper.options.origin : {x: 0, y: 0};
                    this.$box.css({
                        left: bbox.x * this.scale + origin.x,
                        top: bbox.y * this.scale + origin.y,
                        transform: `scale(${scale})`,
                        'transform-origin': '0 0'
                    });
                });
                return this;
            },
            updateBox: function () {
                let bbox = this.model.getBBox();
                if (this.model !== this.scope.model) {
                    this.scope.onChange = this.model.get('onChange');
                    this.scope.sourceId = this.model.get('id');
                    this.scope.model = this.model;
                }

                let origin = this.paper ? this.paper.options.origin : {x: 0, y: 0};

                this.$box.css({
                    width: bbox.width,
                    height: bbox.height,
                    left: bbox.x * this.scale + origin.x,
                    top: bbox.y * this.scale + origin.y
                });
            },
            removeBox: function () {
                this.$box.remove();
            }
        });

        this.workspaceElement = this.$element[0].children[0];
        this.comparison = [false, false];
        this.cellSize = [400, 200];
        this.paddingFactor = 0.8;
        this.nodeSeparationFactor = 0.2;
        this.panActive = false;
        this.initContextMenus();
        this.extractInputs();
        this.extractShapes();
        this.initDiagram();
    }

    $onDestroy() {
        if (this.isComparing) {
            this.cancelComparison();
        }
        if (this.onWindowResize) {
            this.$window.removeEventListener('resize', this.onWindowResize);
        }
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
                drawGrid: {
                    name: 'doubleMesh',
                    args: [
                        {thickness: 1, scaleFactor: 6},
                        {color: 'lightgrey', thickness: 1, scaleFactor: 6}
                    ]
                },
                model: this.graph,
                clickThreshold: 4,
                interactive: false
            });
            this.paper.drawGrid({
                color: '#aaa',
                thickness: 1
            });
            this.paper.on('cell:pointerdown', this.onCellClick.bind(this));
            this.paper.on('blank:pointerclick', this.onPaperClick.bind(this));
            this.paper.on('blank:pointerdown', () => {
                this.panActive = true;
                this.$scope.$evalAsync();
            });
            this.paper.on('blank:pointerup', () => {
                this.panActive = false;
                delete this.lastMousePos;
                this.$scope.$evalAsync();
            });
            this.paper.$el.on('mousewheel', this.onMouseWheel.bind(this));

            this.onWindowResize = () => {
                let width = this.$element[0].offsetWidth;
                let height = this.$element[0].offsetHeight;
                this.paper.setDimensions(
                    width, height
                );
            };
            this.$window.addEventListener('resize', this.onWindowResize);
            this.$element.on('mousemove', this.onMouseMove.bind(this));
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
            this.scaleToContent();
        }
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
        this.setZoom(ratio, {x: 0, y: 0});

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

        if (mouseEvent.originalEvent.wheelDelta > 0) {
            this.zoomIn(localpoint);
        } else {
            this.zoomOut(localpoint);
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
            this.lastMousePos = {x: mouseEvent.offsetX, y: mouseEvent.offsetY};
            let origin = this.paper.options.origin;
            this.paper.translate(origin.x - translate.x, origin.y - translate.y);
            this.$scope.$evalAsync();
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
                let rectAttrs = Object.assign({
                    id: input.id,
                    label: this.getToolLabel(input),
                    type: input.type ? input.type : 'function',
                    inputs: rectInputs,
                    outputs: rectOutputs,
                    tag: input.tag,
                    ports: ports
                }, {
                    operation: input.apply,
                    value: input.constant
                });

                rectangle = this.constructRect(rectAttrs);

                nodes.set(currentId, rectAttrs);

                shapes.push(rectangle);

                if (input.parent) {
                    let firstPort = input.parent.attributes.ports.items.filter(i => {
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
        return new joint.shapes.html.Element(Object.assign({
            id: config.id,
            size: {
                width: this.cellSize[0],
                height: this.cellSize[1]
            },
            cellType: config.type,
            title: config.label || config.id.toString(),
            operation: config.operation,
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
        }, {
            onChange: this.onParameterChange,
            value: config.value
        }));
    }

    startComparison(id) {
        if (this.clickListener) {
            this.clickListener();
        }

        this.mousetipService.set('Select a node to compare');
        this.isComparing = true;
        this.comparison[0] = id;

        let initialClick = true;
        const onClick = () => {
            if (!initialClick) {
                this.cancelComparison();
                this.$document.off('click', this.clickListener);
                this.$scope.$evalAsync();
                delete this.clickListener;
            } else {
                initialClick = false;
            }
        };
        this.clickListener = onClick;
        this.$document.on('click', onClick);
    }

    continueComparison(cv) {
        this.clickListener();
        this.comparison[1] = cv.model.id;
        this.onPreview({data: this.comparison});
        this.unselectCell();
    }

    cancelComparison() {
        this.isComparing = false;
        this.unselectCell();
        this.mousetipService.remove();
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

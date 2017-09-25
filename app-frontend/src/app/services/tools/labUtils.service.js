/* globals joint $ _ */

const Map = require('es6-map');

export default (app) => {
    class LabUtils {
        constructor(toolService, $rootScope, $compile, colorSchemeService, histogramService) {
            'ngInject';

            let viridis = colorSchemeService.defaultColorSchemes.find(s => s.label === 'Viridis');
            let linspace = this.linspace;

            joint.shapes.html = {};
            joint.shapes.html.Element = joint.shapes.basic.Rect.extend({
                defaults: joint.util.deepSupplement({
                    type: 'html.Element',
                    attrs: {
                        rect: {
                            stroke: 'none',
                            'fill-opacity': 0
                        }
                    }
                }, joint.shapes.basic.Rect.prototype.defaults)
            });

            joint.shapes.html.ElementView = joint.dia.ElementView.extend({
                template: `
                <div class="diagram-cell">
                  <rf-diagram-node-header
                    data-model="model"
                    data-invalid="model.get('invalid')"
                  ></rf-diagram-node-header>
                  <div class="node-actions">
                    <div class="node-button-group">
                      <button class="btn node-button" type="button"
                              ng-if="onPreview && model.get('cellType') !== 'const'"
                              ng-click="onPreview($event, this.model)">
                      <span class="icon-map"></span>
                      </button>
                      <button class="btn node-button" type="button"
                              ng-if="model.get('cellType') !== 'const'"
                              ng-class="{'active': currentView === 'HISTOGRAM'}"
                              ng-click="toggleHistogram()">
                      <span class="icon-histogram"></span>
                      </button>
                      <button class="btn node-button" type="button"
                              ng-if="model.get('cellType') !== 'const'"
                              ng-class="{'active': currentView === 'STATISTICS'}"
                              ng-click="toggleStatistics()">
                          Stats
                      </button>
                      <button class="btn node-button" type="button"
                              ng-click="toggleCollapse()">
                      <span ng-class="{'icon-caret-up': showBody,
                                       'icon-caret-down': !showBody}"></span>
                      </button>
                    </div>
                  </div>
                  <rf-input-node
                    ng-if="ifCellType('src')"
                    ng-show="showCellBody()"
                    data-model="model"
                    on-change="onChange({sourceId: sourceId, project: project, band: band})"
                  ></rf-input-node>
                  <rf-operation-node
                    ng-if="ifCellType('function')"
                    ng-show="showCellBody()"
                    data-model="model"
                  ></rf-operation-node>
                  <rf-constant-node
                    ng-if="ifCellType('const')"
                    ng-show="showCellBody()"
                    data-model="model"
                    on-change="onChange({override: override})"
                  ></rf-constant-node>
                  <rf-classify-node
                    ng-if="ifCellType('classify')"
                    ng-show="showCellBody"
                    data-model="model"
                    on-change="onChange({override: override})"
                  ></rf-classify-node>
                  <rf-node-histogram
                    ng-if="currentView === 'HISTOGRAM' && !isCollapsed"
                    data-histogram="histogram"
                    data-breakpoints="breakpoints"
                    data-masks="masks"
                    data-options="histogramOptions"
                    on-breakpoint-change="onBreakpointChange(breakpoints, options)"
                  ></rf-node-histogram>
                  <rf-node-statistics
                    ng-if="currentView === 'STATISTICS' && !isCollapsed"
                    data-model="model"
                    data-toolrun="model.get('toolrun')"
                    data-size="model.get('size')"
                  ></rf-node-statistics>
                </div>`,
                initialize: function () {
                    _.bindAll(this, 'updateBox');
                    joint.dia.ElementView.prototype.initialize.apply(this, arguments);
                    this.model.on('change', this.updateBox, this);
                    this.$box = angular.element(this.template);
                    this.scope = $rootScope.$new();
                    // Acceptable values are 'BODY', 'HISTOGRAM', and 'STATISTICS'
                    this.scope.currentView = 'BODY';
                    this.scope.isCollapsed = false;
                    this.baseWidth = 400;
                    this.histogramHeight = 250;
                    this.statisticsHeight = 200;
                    this.bodyHeight = null;

                    this.scope.toggleHistogram = () => {
                        if (this.scope.isCollapsed) {
                            this.scope.toggleCollapse();
                        }
                        if (this.scope.currentView === 'BODY' && !this.bodyHeight) {
                            this.bodyHeight = this.model.getBBox().height;
                        }
                        if (this.scope.currentView === 'HISTOGRAM') {
                            this.scope.currentView = 'BODY';
                            this.model.resize(this.baseWidth, this.bodyHeight);
                        } else {
                            this.scope.currentView = 'HISTOGRAM';
                            this.expandedSize = this.model.getBBox();
                            this.model.resize(this.baseWidth, this.histogramHeight);
                        }
                    };

                    this.scope.toggleStatistics = () => {
                        if (this.scope.isCollapsed) {
                            this.scope.toggleCollapse();
                        }
                        if (this.scope.currentView === 'BODY' && !this.bodyHeight) {
                            this.bodyHeight = this.model.getBBox().height;
                        }
                        if (this.scope.currentView === 'STATISTICS') {
                            this.scope.currentView = 'BODY';
                            this.model.resize(this.baseWidth, this.bodyHeight);
                        } else {
                            this.scope.currentView = 'STATISTICS';
                            this.model.resize(this.baseWidth, this.statisticsHeight);
                        }
                    };

                    this.scope.toggleCollapse = () => {
                        if (this.scope.currentView === 'BODY' && !this.bodyHeight) {
                            this.bodyHeight = this.model.getBBox().height;
                        }
                        if (this.scope.isCollapsed) {
                            this.model.resize(this.baseWidth, this.scope.lastSize.height);
                            this.scope.isCollapsed = false;
                        } else {
                            this.scope.lastSize = this.model.getBBox();
                            this.model.resize(this.baseWidth, 50);
                            this.scope.isCollapsed = true;
                        }
                    };

                    this.scope.ifCellType = (type) => {
                        return this.scope.model.get('cellType') === type;
                    };

                    this.scope.showCellBody = () => {
                        return (
                            this.scope.currentView === 'BODY' &&
                                !this.scope.isCollapsed
                        );
                    };


                    this.scope.updateBreakpoints = () => {
                        let newBreakpoints = this.scope.breakpoints.reduce((map, obj) => {
                            if (obj) {
                                map[obj.value.toString()] = obj.color;
                            }
                            return map;
                        }, {});
                        let clip = 'none';
                        if (this.scope.histogramOptions.masks) {
                            if (this.scope.histogramOptions.masks.min &&
                                this.scope.histogramOptions.masks.max) {
                                clip = 'both';
                            } else if (this.scope.histogramOptions.masks.min) {
                                clip = 'left';
                            } else if (this.scope.histogramOptions.masks.max) {
                                clip = 'right';
                            }
                        }
                        let renderDef = {
                            scale: this.scope.histogramOptions.scale ?
                                this.scope.histogramOptions.scale : 'SEQUENTIAL',
                            breakpoints: newBreakpoints,
                            clip: clip
                        };
                        this.scope.onChange({
                            renderDef: {
                                id: this.model.get('id'),
                                value: renderDef
                            }
                        });
                    };

                    this.scope.onBreakpointChange = (breakpoints, options) => {
                        this.scope.breakpoints = breakpoints;
                        this.scope.histogramOptions = options;
                        this.scope.updateBreakpoints();
                    };

                    this.scope.toggleBody = () => {
                        this.scope.showBody = !this.scope.showBody;
                        if (!this.scope.showBody) {
                            if (!this.scope.showHistogram) {
                                this.expandedSize = this.model.getBBox();
                            }
                            this.model.resize(this.expandedSize.width, 50);
                        } else if (this.scope.showHistogram) {
                            this.model.resize(this.expandedSize.width, this.histogramHeight);
                        } else {
                            this.model.resize(this.expandedSize.width, this.expandedSize.height);
                        }
                    };

                    let previewCallback = _.first(
                        this.model.get('contextMenu')
                            .filter((item) => item.label === 'View output')
                            .map((item) => item.callback)
                    );

                    this.scope.onPreview = () => {
                        let histogramToolRun = this.scope.model.get('histogramToolRun');
                        if (!histogramToolRun) {
                            let updateToolRun = this.scope.model.get('updateToolRun');
                            this.model.prop('histogramToolRun', this.scope.toolrun.id);
                            toolService
                                .getNodeHistogram(this.scope.toolrun.id, this.model.attributes.id)
                                .then((histogram) => {
                                    this.model.prop('histogram', histogram);
                                    this.scope.histogram = histogram;
                                    let newRange = {min: histogram.minimum, max: histogram.maximum};
                                    histogramService.scaleBreakpointsToRange(
                                        this.scope.breakpoints,
                                        this.scope.histogramOptions.range,
                                        newRange
                                    );
                                    this.scope.histogramOptions.range = newRange;
                                    this.scope.updateBreakpoints();
                                    updateToolRun()
                                        .then(()=> {
                                            previewCallback(
                                                null, this.scope.model
                                            );
                                        });
                                });
                        } else if (histogramToolRun) {
                            previewCallback(null, this.scope.model);
                        }
                    };

                    $compile(this.$box)(this.scope);

                    this.updateBox();
                },
                render: function () {
                    joint.dia.ElementView.prototype.render.apply(this, arguments);
                    this.paper.$el.prepend(this.$box);
                    this.updateBox();
                    this.listenTo(this.paper, 'translate', () => {
                        let bbox = this.model.getBBox();
                        let origin = this.paper ? this.paper.options.origin : {
                            x: 0,
                            y: 0
                        };
                        this.$box.css({
                            left: bbox.x * this.scale + origin.x,
                            top: bbox.y * this.scale + origin.y
                        });
                    });
                    this.scale = 1;
                    this.listenTo(this.paper, 'scale', (scale) => {
                        this.scale = scale;
                        let bbox = this.model.getBBox();
                        let origin = this.paper ? this.paper.options.origin : {
                            x: 0,
                            y: 0
                        };
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
                    if (!_.isEqual(this.model, this.scope.model)) {
                        this.scope.onChange = this.model.get('onChange');
                        this.scope.sourceId = this.model.get('id');
                        this.scope.model = this.model;
                    }

                    if (!this.scope.breakpoints) {
                        let breakpoints = linspace(0, 255, viridis.colors.length);
                        this.scope.breakpoints = breakpoints.map((value, index) => {
                            return {value: value, color: viridis.colors[index]};
                        });
                        this.scope.histogramOptions = {
                            range: {min: 0, max: 255},
                            masks: {min: false, max: false},
                            scale: 'SEQUENTIAL'
                        };
                        this.scope.updateBreakpoints();
                    }
                    this.scope.toolrun = this.model.get('toolrun');
                    let histogramToolRun = this.model.get('histogramToolRun');
                    if (this.scope.currentView === 'HISTOGRAM' && !this.scope.isCollapsed &&
                        this.scope.toolrun &&
                        histogramToolRun !== this.scope.toolrun.id
                       ) {
                        this.model.prop('histogramToolRun', this.scope.toolrun.id);
                        toolService
                            .getNodeHistogram(this.scope.toolrun.id, this.model.attributes.id)
                            .then((histogram) => {
                                this.model.prop('histogram', histogram);
                                this.scope.histogram = histogram;
                            });
                    } else {
                        this.scope.histogram = this.model.get('histogram');
                    }

                    let origin = this.paper ? this.paper.options.origin : {
                        x: 0,
                        y: 0
                    };

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
        }
        getToolImports(toolDefinition) {
            let inputsJson = [];

            let json = Object.assign({}, toolDefinition);
            let inputs = [json];
            while (inputs.length) {
                let input = inputs.pop();
                let args = input.args;
                if (args) {
                    let tool = this.getNodeLabel(input);
                    if (!Array.isArray(args)) {
                        args = Object.values(args);
                    }
                    inputs = inputs.concat(args.map((a) => {
                        return Object.assign({
                            parent: tool
                        }, a);
                    }));
                } else {
                    inputsJson.push(input);
                }
            }
            return inputsJson;
        }

        getNodeLabel(json) {
            if (json.metadata && json.metadata.label) {
                return json.metadata.label;
            }
            return json.apply;
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

        getNodeArgs(node) {
            if (node.args) {
                return Array.isArray(node.args) ? node.args : Object.values(node.args);
            }
            return [];
        }

        getNodeAttributes(node) {
            let rectInputs = this.getNodeArgs(node).length;
            let rectOutputs = ['Output'];
            let ports = this.createPorts(rectInputs, rectOutputs);
            return Object.assign({
                id: node.id,
                label: this.getNodeLabel(node),
                type: this.getNodeType(node),
                inputs: rectInputs,
                outputs: rectOutputs,
                tag: node.tag,
                ports: ports
            }, {
                operation: node.apply,
                metadata: node.metadata,
                classMap: node.classMap,
                value: node.constant,
                positionOverride: node.metadata && node.metadata.positionOverride
            });
        }

        getNodeType(node) {
            if (node.type) {
                return node.type;
            } else if (node.apply === 'classify') {
                return 'classify';
            }
            return 'function';
        }

        constructRect(config, contextMenu, onChangeFn, dimensions, updateToolRunFn) {
            return new joint.shapes.html.Element(Object.assign({
                id: config.id,
                size: {
                    width: dimensions.width,
                    height: dimensions.height
                },
                cellType: config.type,
                title: config.label || config.id.toString(),
                operation: config.operation,
                metadata: config.metadata,
                classMap: config.classMap,
                contextMenu: contextMenu,
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
                onChange: onChangeFn,
                updateToolRun: updateToolRunFn,
                value: config.value,
                positionOverride: config.positionOverride
            }));
        }

        linspace(min, max, num) {
            let n = num;
            if (typeof n === 'undefined') {
                n = Math.max(Math.round(max - min) + 1, 1);
            }
            if (n < 2) {
                return n === 1 ? [min] : [];
            }
            let i = Array(n);
            let ret = Array(n);
            n = n - 1;
            for (i = n; i >= 0; i = i - 1) {
                ret[i] = (i * max + (n - i) * min) / n;
            }
            return ret;
        }

        extractShapes(
            toolDefinition, defaultContextMenu, onParameterChange, cellDimensions, updateToolRunFn
        ) {
            let nodes = new Map();
            let shapes = [];
            let json = Object.assign({}, toolDefinition);
            let inputs = [json];

            while (inputs.length) {
                let input = inputs.pop();
                let rectangle;

                // Input nodes not of the layer type are not made into rectangles
                if (!input.type || input.type === 'src' || input.type === 'const') {
                    let rectAttrs = this.getNodeAttributes(input);

                    rectangle = this.constructRect(
                        rectAttrs,
                        defaultContextMenu,
                        onParameterChange,
                        cellDimensions,
                        updateToolRunFn
                    );

                    nodes.set(input.id, rectAttrs);

                    shapes.push(rectangle);

                    if (input.parent) {
                        let firstPort = input.parent.attributes.ports.items.filter(i => {
                            return i.group === 'inputs' && !i.isConnected;
                        })[0];

                        firstPort.isConnected = true;

                        let link = new joint.dia.Link({
                            source: {
                                id: rectangle.id,
                                port: 'Output'
                            },
                            target: {
                                id: input.parent.id,
                                port: firstPort.id
                            },
                            attrs: {
                                '.marker-target': {
                                    d: 'M 4 0 L 0 2 L 4 4 z'
                                },
                                'g.link-tools': {
                                    display: 'none'
                                },
                                'g.marker-arrowheads': {
                                    display: 'none'
                                },
                                '.connection-wrap': {
                                    display: 'none'
                                }
                            }
                        });

                        shapes.push(link);
                    }
                    inputs = inputs.concat(
                        this.getNodeArgs(input)
                            .map((a) => {
                                return Object.assign({
                                    parent: rectangle
                                }, a);
                            })
                    );
                }
            }
            return {
                shapes, nodes
            };
        }
    }


    app.service('labUtils', LabUtils);
};

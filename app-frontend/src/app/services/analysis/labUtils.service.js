/* globals joint $ _ */
// TODO tear out all references to tool run - it should use redux to pull in the correct stuff
import {Map} from 'immutable';
import {getNodeArgs} from '_redux/node-utils';

export default (app) => {
    class LabUtils {
        constructor($rootScope, $compile) {
            'ngInject';

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
                template: '<rf-lab-node node-id="nodeId" model="model"></rf-lab-node>',
                initialize: function () {
                    _.bindAll(this, 'updateBox');
                    joint.dia.ElementView.prototype.initialize.apply(this, arguments);
                    this.model.on('change', this.updateBox, this);
                    this.$box = angular.element(this.template);
                    this.scope = $rootScope.$new();

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
                        this.scope.nodeId = this.model.get('id');
                        this.scope.model = this.model;
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

                    this.scope.updateTick = new Date().getTime();
                },
                removeBox: function () {
                    this.$box.remove();
                }
            });
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

        getNodeAttributes(node) {
            let rectInputs = getNodeArgs(node).length;
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

        getNodeLabel(json) {
            if (json.metadata && json.metadata.label) {
                return json.metadata.label;
            }
            return json.apply;
        }

        getNodeType(node) {
            if (node.type) {
                return node.type;
            } else if (node.apply === 'classify') {
                return 'classify';
            }
            return 'function';
        }

        constructRect(config, dimensions) {
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
                value: config.value,
                positionOverride: config.positionOverride
            }));
        }

        extractShapes(
            ast, cellDimensions
        ) {
            let nodes = new Map();
            let shapes = [];
            let json = Object.assign({}, ast);
            let inputs = [json];

            while (inputs.length) {
                let input = inputs.pop();
                let rectangle;

                // Old ast's name 'projectSrc' input nodes as 'src'. New ast's use 'projectSrc'
                // In the future, we may want to write a migration to move them over.
                if (input.type === 'src') {
                    input.type = 'projectSrc';
                }

                // Input nodes not of the layer type are not made into rectangles
                if (!input.type || input.type === 'projectSrc' || input.type === 'const') {
                    let rectAttrs = this.getNodeAttributes(input);

                    rectangle = this.constructRect(
                        rectAttrs,
                        cellDimensions
                    );

                    nodes = nodes.set(input.id, rectAttrs);

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
                                'g.link-ast': {
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
                        getNodeArgs(input).reverse()
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

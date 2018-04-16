/* globals joint $ _ */

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
    }

    app.service('labUtils', LabUtils);
};

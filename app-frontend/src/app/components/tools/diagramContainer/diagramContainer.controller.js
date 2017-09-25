/* global joint $ */

const maxZoom = 3;
const minZoom = 0.025;

export default class DiagramContainerController {
    constructor( // eslint-disable-line max-params
        $element, $scope, $state, $timeout, $compile, $document, $window, $rootScope,
        mousetipService, toolService, labUtils, colorSchemeService
    ) {
        'ngInject';
        this.$element = $element;
        this.$scope = $scope;
        this.$rootScope = $rootScope;
        this.$state = $state;
        this.$timeout = $timeout;
        this.$compile = $compile;
        this.$document = $document;
        this.$window = $window;
        this.mousetipService = mousetipService;
        this.toolService = toolService;
        this.labUtils = labUtils;
        this.colorSchemeService = colorSchemeService;
    }

    $onInit() {
        this.scale = 1;

        this.$scope.$on('$destroy', this.$onDestroy.bind(this));

        this.workspaceElement = this.$element[0].children[0];
        this.comparison = [false, false];
        this.cellDimensions = {width: 400, height: 200 };
        this.paddingFactor = 0.8;
        this.nodeSeparationFactor = 0.2;
        this.panActive = false;
        this.initContextMenus();

        this.inputsJson = this.labUtils.getToolImports(this.toolDefinition);

        let extract = this.labUtils.extractShapes(
            this.toolDefinition,
            this.defaultContextMenu,
            this.onParameterChange,
            this.cellDimensions,
            this.createToolRun
        );
        this.shapes = extract.shapes;
        this.nodes = extract.nodes;
        this.onGraphComplete({nodes: this.nodes});


        this.initDiagram();

        this.$rootScope.$on('lab.resize', () => {
            this.$timeout(this.onWindowResize, 100);
        });
    }

    $onChanges(changes) {
        if (changes.toolrun && this.shapes) {
            let toolrun = changes.toolrun.currentValue;
            this.shapes.forEach((shape) => {
                let model = this.paper.getModelById(shape.attributes.id);
                model.prop('toolrun', toolrun);
            });
        }
    }

    $onDestroy() {
        if (this.isComparing) {
            this.cancelComparison();
        }
        if (this.onWindowResize) {
            this.$window.removeEventListener('resize', this.onWindowResize);
        }
    }

    initDiagram() {
        if (!this.graph) {
            this.graph = new joint.dia.Graph();
        } else {
            this.graph.clear();
        }

        if (!this.paper) {
            let el = $(this.$element);
            let height = el.height();
            if (height === 0) {
                let resetDimensions = () => {
                    let elHeight = el.height();
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
            this.paper.$el.on('wheel', this.onMouseWheel.bind(this));

            this.onWindowResize = () => {
                let owidth = this.$element[0].offsetWidth;
                let oheight = this.$element[0].offsetHeight;
                this.paper.setDimensions(
                    owidth, oheight
                );
            };
            this.$window.addEventListener('resize', this.onWindowResize);
            this.$element.on('mousemove', this.onMouseMove.bind(this));
        }

        if (this.shapes) {
            let padding = this.cellDimensions.width * this.nodeSeparationFactor;
            this.shapes.forEach(s => this.graph.addCell(s));
            joint.layout.DirectedGraph.layout(this.graph, {
                setLinkVertices: false,
                rankDir: 'LR',
                nodeSep: padding,
                rankSep: padding * 2,
                marginX: padding,
                marginY: padding
            });
            this.overridePositions(this.graph);
            this.scaleToContent();
        }
    }

    overridePositions(graph) {
        // eslint-disable-next-line
        Object.keys(graph._nodes)
            .map((modelid) => this.paper.getModelById(modelid))
            .forEach((model) => {
                if (model.attributes.positionOverride) {
                    model.position(
                        model.attributes.positionOverride.x,
                        model.attributes.positionOverride.y
                    );
                }
            });
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
        this.setZoom(ratio > 1 ? 1 : ratio, {
            x: 0,
            y: 0
        });

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

        if (mouseEvent.originalEvent.deltaY < 0) {
            let newZoom = this.scale * (1 + mouseEvent.originalEvent.deltaY * -0.002);
            this.setZoom(newZoom, localpoint);
        } else {
            let newZoom = this.scale / (1 + mouseEvent.originalEvent.deltaY * 0.002);
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
            this.lastMousePos = {
                x: mouseEvent.offsetX,
                y: mouseEvent.offsetY
            };
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
                return this.onPreview({
                    nodeId: model.get('id')
                });
            }
        }, {
            type: 'divider'
        }, {
            label: 'Share',
            callback: ($event, model) => {
                return this.onShare({
                    nodeId: model.get('id')
                });
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
        this.onPreview({
            data: this.comparison
        });
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
}

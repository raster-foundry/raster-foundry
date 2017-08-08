/* global L */
import { FrameView } from '../../../components/map/labMap/frame.module.js';

const tileLayerOptions = {maxZoom: 30};

export default class LabRunController {
    constructor( // eslint-disable-line max-params
        $log, $scope, $timeout, $element, $window, $document, $uibModal, $rootScope,
        mapService, projectService, authService, mapUtilsService, toolService,
        APP_CONFIG
    ) {
        'ngInject';
        this.$log = $log;
        this.$scope = $scope;
        this.$rootScope = $rootScope;
        this.$parent = $scope.$parent.$ctrl;
        this.$timeout = $timeout;
        this.$element = $element;
        this.$window = $window;
        this.$document = $document;
        this.$uibModal = $uibModal;
        this.authService = authService;
        this.projectService = projectService;
        this.mapUtilsService = mapUtilsService;
        this.toolService = toolService;
        this.getMap = () => mapService.getMap('lab-preview');
        this.tileServer = `${APP_CONFIG.tileServerLocation}`;

        this.showDiagram = true;
    }

    $onInit() {
        this.$parent.toolRequest.then(tool => {
            this.tool = tool;
            // This will be a call to the API
            this.sources = this.generateSourcesFromTool();
            this.toolRun = this.generateToolRun();
            this.generatedPreview = false;
        });
        this.setWarning(
             'You must apply changes after defining inputs.'
        );

        this.$scope.$on('$destroy', () => {
            $('body').css({overflow: ''});
        });
        $('body').css({overflow: 'hidden'});

        this.singlePreviewPosition = {x: 0, offset: 10, side: 'none'};
    }

    generateSourcesFromTool() {
        let nodes = [this.tool.definition];
        let sources = [];
        let sourceIds = [];
        let currentNode = 0;
        let shouldContinue = true;
        while (shouldContinue) {
            let args = nodes[currentNode].args || false;
            if (args) {
                nodes = nodes.concat(args);
            }
            currentNode += 1;
            shouldContinue = currentNode < nodes.length;
        }
        nodes.forEach(n => {
            if (!n.apply && n.type === 'src') {
                if (sourceIds.indexOf(n.id) < 0) {
                    sourceIds.push(n.id);
                    sources.push(n);
                }
            }
        });
        return sources;
    }

    generateToolRun() {
        return this.sources.reduce((tr, s) => {
            tr.executionParameters.sources[s.id] = {
                id: false,
                band: null,
                type: 'project'
            };
            return tr;
        }, {
            visibility: 'PUBLIC',
            tool: this.tool.id,
            executionParameters: {
                sources: {}
            }
        });
    }

    getNodeUrl(node) {
        let token = this.authService.token();
        if (this.lastToolRun) {
            // eslint-disable-next-line max-len
            return `${this.tileServer}/tools/${this.lastToolRun.id}/{z}/{x}/{y}?token=${token}&node=${node}`;
        }
        return false;
    }

    updatePreviewLayers() {
        if (!this.previewLayers) {
            this.createPreviewLayers();
        } else if (this.previewLayers.length === 2) {
            this.previewLayers.forEach((l, i) => {
                l.setUrl(this.getNodeUrl(this.previewData[i]));
            });
        } else {
            this.previewLayers[0].setUrl(this.getNodeUrl(this.previewData));
        }
    }

    createPreviewLayers() {
        if (this.previewLayers) {
            this.previewLayers.forEach(l => l.remove());
        }
        if (this.previewData.constructor === Array) {
            let url0 = this.getNodeUrl(this.previewData[0]);
            let url1 = this.getNodeUrl(this.previewData[1]);
            if (url0 && url1) {
                this.previewLayers = [
                    L.tileLayer(url0, tileLayerOptions),
                    L.tileLayer(url1, tileLayerOptions)
                ];
            }
        } else {
            let url0 = this.getNodeUrl(this.previewData);
            if (url0) {
                this.previewLayers = [L.tileLayer(url0, tileLayerOptions)];
            }
        }
    }

    addFrames() {
        if (!this.frameControl) {
            this.frameControl = L.control.frames({});
        }

        if (!this.frameControlAdded) {
            this.frameControlAdded = true;
            this.getMap().then((m) => {
                this.frameControl.addTo(m.map);
                let frame = this.frameControl.getFrame();
                let mapSize = m.map.getSize();
                frame.dimensions = Object.assign(
                    frame.dimensions,
                    {width: mapSize.x, height: mapSize.y}
                );
                this.leftFrame = new FrameView();
                this.leftFrame.dimensions = {
                    x: 0, y: 0,
                    width: mapSize.x / 2,
                    height: mapSize.y
                };
                this.leftFrame.children = [this.previewLayers[0]];
                this.rightFrame = new FrameView();
                this.rightFrame.dimensions = {
                    x: mapSize.x / 2, y: 0,
                    width: mapSize.x / 2,
                    height: mapSize.y
                };
                this.rightFrame.children = [this.previewLayers[1]];
                frame.children = [this.leftFrame, this.rightFrame];
                this.dividerPosition = 0.5;
                frame.onUpdate = (dividers) => {
                    let currentPosition = this.dividerPosition;
                    this.dividerPosition = dividers.length ? dividers[0].position : 0;
                    if (this.dividerPosition !== currentPosition) {
                        this.$scope.$evalAsync();
                    }
                };
            });
        } else {
            this.leftFrame.children = [this.previewLayers[0]];
            this.rightFrame.children = [this.previewLayers[1]];
        }
    }

    showPreview(data) {
        let defaultSplit = 40;
        if (!this.lastToolRun) {
            return;
        }

        if (!this.isShowingPreview) {
            this.isShowingPreview = true;
            this.splitPercentX = this.splitPercentX || defaultSplit;
            this.setPartitionStyles(this.splitPercentX);
        }

        if (data) {
            this.previewData = data;
            this.createPreviewLayers();
            if (this.previewLayers) {
                this.generatedPreview = true;
                this.getMap().then(m => {
                    this.previewLayers.forEach(l => l.addTo(m.map));
                    if (data instanceof Array) {
                        this.addFrames();
                    } else if (this.frameControl) {
                        this.frameControl.remove();
                        this.frameControlAdded = false;
                    }
                    if (!this.alreadyPreviewed) {
                        this.alreadyPreviewed = true;
                        this.$timeout(() => {
                            let s = this.lastToolRun.executionParameters.sources;
                            let firstSourceId = Object.keys(s)[0];
                            this.projectService.get(s[firstSourceId].id).then(p => {
                                this.fitProjectExtent(p);
                            });
                        });
                    }
                });
            }
        }
    }

    setPartitionStyles(percentRatio) {
        this.showMap = percentRatio > 10;
        this.showDiagram = percentRatio < 90;
        if (percentRatio >= 0 && percentRatio <= 100) {
            this.labLeftStyle = {width: `${percentRatio}%`};
            this.labRightStyle = {width: `${100 - percentRatio}%`};
            this.resizeHandleStyle = {left: `${percentRatio}%`};
        }
        this.getMap().then((mapWrapper) => {
            this.$timeout(() => {
                mapWrapper.map.invalidateSize();
            }, 100);
        });
        this.$rootScope.$broadcast('lab.resize');
    }

    resetPartitionStyles() {
        this.labLeftStyle = {};
        this.labRightStyle = {};
        this.resizeHandleStyle = {};

        this.getMap().then((mapWrapper) => {
            this.$timeout(() => {
                mapWrapper.map.invalidateSize();
            }, 100);
        });
    }

    clearTextSelections() {
        if (this.$window.getSelection && this.$window.getSelection().empty) {
            this.$window.getSelection().empty();
        } else if (this.$window.getSelection().removeAllRanges) {
            this.$window.getSelection().removeAllRanges();
        } else if (this.$document.selection) {
            this.$document.selection.empty();
        }
    }

    startLabSplitDrag(event) {
        if (this.labResizing) {
            this.resizeStopListener();
        }
        this.labResizing = true;
        this.resizeMoveListener = (resizeEvent) => {
            this.clearTextSelections();

            this.splitPercentX = resizeEvent.pageX / this.$element.width() * 100;
            if (this.splitPercentX > 80) {
                if (this.splitPercentX > 90) {
                    this.labResizingStyle = {
                        left: '95%',
                        width: '10%'
                    };
                } else {
                    this.splitPercentX = 80;
                    this.labResizingStyle = {
                        left: `${this.splitPercentX}%`,
                        // eslint-disable-next-line no-undefined
                        width: undefined
                    };
                }
            } else if (this.splitPercentX < 20) {
                if (this.splitPercentX < 10) {
                    this.labResizingStyle = {
                        left: '5%',
                        width: '10%'
                    };
                } else {
                    this.splitPercentX = 20;
                    this.labResizingStyle = {
                        left: `${this.splitPercentX}%`,
                        // eslint-disable-next-line no-undefined
                        width: undefined
                    };
                }
            } else {
                this.labResizingStyle = {
                    left: `${this.splitPercentX}%`,
                    // eslint-disable-next-line no-undefined
                    width: undefined
                };
            }
            this.$scope.$evalAsync();
        };
        this.resizeStopListener = (resizeStopEvent) => {
            this.$element.off('mousemove', this.resizeMoveListener);
            this.$element.off('mouseup', this.resizeStopListener);
            this.labResizing = false;
            this.$scope.$evalAsync();

            if (resizeStopEvent.pageX) {
                this.splitPercentX = resizeStopEvent.pageX / this.$element.width() * 100;
                if (this.splitPercentX > 80) {
                    if (this.splitPercentX > 90) {
                        this.splitPercentX = 100;
                        this.setPartitionStyles(100);
                    } else {
                        this.splitPercentX = 80;
                        this.setPartitionStyles(this.splitPercentX);
                    }
                } else if (this.splitPercentX < 20) {
                    if (this.splitPercentX < 10) {
                        this.splitPercentX = 0;
                        this.setPartitionStyles(0);
                    } else {
                        this.splitPercentX = 20;
                        this.setPartitionStyles(this.splitPercentX);
                    }
                } else {
                    this.setPartitionStyles(this.splitPercentX);
                }
            }
            this.$element.css({
                // eslint-disable-next-line no-undefined
                'user-select': undefined,
                cursor: 'auto'
            });
            this.labResizingStyle = {
                // eslint-disable-next-line no-undefined
                width: undefined
            };
        };
        this.$element.css({
            'user-select': 'none',
            'cursor': 'col-resize'
        });
        this.resizeMoveListener(event);
        this.$element.on('mousemove', this.resizeMoveListener);
        this.$element.on('mouseup', this.resizeStopListener);
    }

    onPreviewClose() {
        this.isShowingPreview = false;
        this.resetPartitionStyles();
        this.$rootScope.$broadcast('lab.resize');
    }

    shareNode(data) {
        if (data) {
            let tileUrl = this.getNodeUrl(data);
            this.publishModal(tileUrl);
        }
    }

    fitProjectExtent(project) {
        this.getMap().then(m => {
            m.map.invalidateSize();
            this.mapUtilsService.fitMapToProject(m, project);
        });
    }

    closePreview() {
        this.isShowingPreview = false;
    }

    createToolRun() {
        this.applyInProgress = true;
        this.clearWarning();
        this.toolService.createToolRun(this.toolRun).then(tr => {
            this.lastToolRun = tr;
            this.clearWarning();
            if (this.isShowingPreview) {
                this.showPreview(this.previewData);
            }
        }, () => {
            this.setWarning(
                'There was an error applying your changes. ' +
                    'Please verify that all inputs are defined.'
            );
        }).finally(() => {
            this.applyInProgress = false;
        });
    }

    onExecutionParametersChange(sourceId, project, band, override) {
        if (project && typeof band === 'number' && band >= 0) {
            this.toolRun.executionParameters.sources[sourceId].id = project.id;
            this.toolRun.executionParameters.sources[sourceId].band = band;
        }
        if (override) {
            if (!this.toolRun.executionParameters.overrides) {
                this.toolRun.executionParameters.overrides = {};
            }
            this.toolRun.executionParameters.overrides[override.id] = {constant: override.value};
        }
    }

    selectProjectModal(sourceId) {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfProjectSelectModal',
            resolve: {
                project: () => this.toolRun.executionParameters.sources[sourceId].id || false,
                content: () => ({
                    title: 'Select a project'
                })
            }
        });

        this.activeModal.result.then(p => {
            this.toolRun.executionParameters.sources[sourceId].id = p.id;
            // eslint-disable-next-line no-underscore-dangle
            this.toolRun.executionParameters.sources[sourceId]._name = p.name;
            this.$scope.$evalAsync();
        });
    }

    publishModal(tileUrl) {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        if (tileUrl) {
            this.activeModal = this.$uibModal.open({
                component: 'rfProjectPublishModal',
                resolve: {
                    tileUrl: () => this.projectService.getBaseURL() + tileUrl,
                    noDownload: () => true
                }
            });
        }
        return this.activeModal;
    }

    setWarning(text) {
        this.warning = text;
    }

    clearWarning() {
        delete this.warning;
    }

    onCompareClick() {
        if (this.frameControlAdded) {
            this.previewData = this.previewData[0];
        } else {
            this.previewData = [this.previewData, this.previewData];
        }
        this.showPreview(this.previewData);
    }

    get leftPreviewSelection() {
        return Array.isArray(this.previewData) && this.previewData[0];
    }

    get leftPreviewPosition() {
        if (!this._leftPreviewPosition || this._leftPreviewPosition.x !== this.dividerPosition) {
            this._leftPreviewPosition = {x: this.dividerPosition, offset: 10, side: 'left'};
        }
        return this._leftPreviewPosition;
    }

    get rightPreviewSelection() {
        return Array.isArray(this.previewData) && this.previewData[1];
    }

    get rightPreviewPosition() {
        if (!this._rightPreviewPosition || this._rightPreviewPosition.x !== this.dividerPosition) {
            this._rightPreviewPosition = {x: this.dividerPosition, offset: 10, side: 'right'};
        }
        return this._rightPreviewPosition;
    }

    get singlePreviewSelection() {
        return this.previewData;
    }

    onNodeClose(side) {
        this.previewData = this.previewData[side === 'left' ? 1 : 0];
        this.showPreview(this.previewData);
    }

    onGraphComplete(nodes) {
        this.nodeMap = nodes;
    }

    onLeftSelect(id) {
        this.previewData[0] = id;
        this.showPreview(this.previewData);
    }

    onRightSelect(id) {
        this.previewData[1] = id;
        this.showPreview(this.previewData);
    }

    onSingleSelect(id) {
        this.previewData = id;
        this.showPreview(this.previewData);
    }

    onLeftClose() {
        this.previewData = this.previewData[1];
        this.showPreview(this.previewData);
    }

    onRightClose() {
        this.previewData = this.previewData[0];
        this.showPreview(this.previewData);
    }

    onSingleClose() {
        this.onPreviewClose();
    }
}

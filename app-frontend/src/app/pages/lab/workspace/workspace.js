/* global L */
import _ from 'lodash';
import { FrameView } from '../../../components/map/labMap/frame.module.js';
// import AnalysisActions from '_redux/actions/analysis-actions';
import WorkspaceActions from '_redux/actions/workspace-actions';
import NodeActions from '_redux/actions/node-actions';

class LabWorkspaceController {
    constructor(
        $ngRedux, $scope, $rootScope, $state, $timeout, $element, $window, $document, modalService,
        mapService, projectService, authService, mapUtilsService, analysisService, tokenService,
        workspaceService,
        APP_CONFIG
    ) {
        'ngInject';
        this.$scope = $scope;
        this.$rootScope = $rootScope;
        this.$state = $state;
        this.$timeout = $timeout;
        this.$element = $element;
        this.$window = $window;
        this.$document = $document;
        this.modalService = modalService;

        let unsubscribe = $ngRedux.connect(
            this.mapStateToThis,
            Object.assign({}, WorkspaceActions, NodeActions)
        )(this);
        $scope.$on('$destroy', unsubscribe);

        this.getMap = () => mapService.getMap('lab-preview');
        this.projectService = projectService;
        this.authService = authService;
        this.mapUtilsService = mapUtilsService;
        this.analysisService = analysisService;
        this.workspaceService = workspaceService;
        this.tokenService = tokenService;

        this.tileServer = `${APP_CONFIG.tileServerLocation}`;
    }

    mapStateToThis(state) {
        return {
            workspace: state.lab.workspace,
            nodes: state.lab.nodes,
            previewNodes: state.lab.previewNodes,
            user: state.api.user,
            createNode: state.lab.createNode
        };
    }

    $onInit() {
        this.showDiagram = true;

        this.workspaceId = this.$state.params.workspaceid;

        let userWatch = this.$scope.$watch('$ctrl.user', user => {
            if (user) {
                this.initWorkspace();
                userWatch();
            }
        });

        this.$scope.$on('$destroy', () => {
            $('body').css({overflow: ''});
        });
        $('body').css({overflow: 'hidden'});

        this.singlePreviewPosition = {x: 0, offset: 10, side: 'none'};

        this.$scope.$watch('$ctrl.previewNodes', (nodes) => {
            if (nodes && nodes.length) {
                this.showPreview(nodes.length > 1 ? nodes : nodes[0]);
            } else {
                this.onSingleClose();
            }
        });
    }

    initWorkspace() {
        let workspace = this.$state.params.workspace;
        if (this.workspaceId && !workspace) {
            this.fetchWorkspace(this.workspaceId);
        } else if (workspace) {
            this.loadWorkspace(workspace, {readonly: false, controls: true});
            if (!workspace.analyses.length) {
                this.startCreatingNode();
            }
        } else if (!this.workspaceId) {
            this.$state.go('lab.browse.workspaces');
        }
    }

    onAnalysisParameterChange(nodeid, project, band, override, renderDef, position) {
        // TODO re-write this for redux pattern
        // TODO this needs to determine which analysis was updated
        if (project && typeof band === 'number' && band >= 0) {
            this.analysisService.updateAnalysisSource(this.workspace, nodeid, project.id, band);
        }
        if (override) {
            this.analysisService.updateAnalysisConstant(this.workspace, override.id, override);
        }
        if (renderDef) {
            let metadata = this.analysisService.getAnalysisMetadata(this.workspace, renderDef.id);
            this.analysisService.updateAnalysisMetadata(
                this.workspace,
                renderDef.id,
                Object.assign({}, metadata, {
                    renderDefinition: renderDef.value
                })
            );
        }
        if (position) {
            let metadata = this.analysisService.getAnalysisMetadata(this.workspace, nodeid);
            this.analysisService.updateAnalysisMetadata(
                this.workspace,
                nodeid,
                Object.assign({}, metadata, {
                    positionOverride: position
                })
            );
        }
    }

    saveAnalysis() {
        this.saveInProgress = true;
        this.clearWarning();
        const analysisSavePromise = this.analysisService.updateAnalysis(
            Object.assign(
                {},
                this.workspace
            )
        ).then(() => {
            if (this.isShowingPreview) {
                this.createPreviewLayers();
                this.showPreview(this.previewData);
            }
        }).finally(() => {
            this.applyInProgress = false;
        });
        return analysisSavePromise;
    }

    shareNode(nodeId) {
        if (nodeId && this.workspace) {
            const node = this.nodes.get(nodeId);
            const analysisId = node.analysisId;
            if (node.type === 'projectSrc') {
                this.tokenService.getOrCreateAnalysisMapToken({
                    organizationId: this.workspace.organizationId,
                    name: this.workspace.title + ' - ' + this.workspace.id,
                    project: node.projId
                }).then((mapToken) => {
                    this.publishModal(
                        this.projectService.getProjectLayerURL(
                            node.projId, {mapToken: mapToken.id}
                        )
                    );
                });
            } else {
                this.tokenService.getOrCreateAnalysisMapToken({
                    organizationId: this.workspace.organizationId,
                    name: this.workspace.title + ' - ' + this.workspace.id,
                    workspace: this.workspace.id
                }).then((mapToken) => {
                    this.publishModal(
                        // eslint-disable-next-line max-len
                        `${this.tileServer}/tools/${analysisId}/{z}/{x}/{y}?mapToken=${mapToken.id}&node=${nodeId}`
                    );
                });
            }
        }
    }

    publishModal(tileUrl) {
        if (tileUrl) {
            this.modalService.open({
                component: 'rfProjectPublishModal',
                resolve: {
                    tileUrl: () => tileUrl,
                    noDownload: () => true,
                    analysisTitle: () => this.workspace.name
                }
            });
        }
        return false;
    }

    fitProjectExtent(project) {
        this.getMap().then(m => {
            m.map.invalidateSize();
            this.mapUtilsService.fitMapToProject(m, project);
        });
    }

    onPreviewClose() {
        this.isShowingPreview = false;
        this.resetPartitionStyles();
        this.$rootScope.$broadcast('lab.resize');
    }

    closePreview() {
        this.isShowingPreview = false;
    }

    setWarning(text) {
        this.warning = text;
    }

    clearWarning() {
        delete this.warning;
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

    flattenAnalysisDefinition(analysisDefinition) {
        let inQ = [analysisDefinition];
        let outQ = [];
        while (inQ.length) {
            let node = inQ.pop();
            outQ.push(node);
            if (node.args) {
                inQ = [
                    ...inQ,
                    ...node.args.map(a => Object.assign({}, a, { parent: node }))
                ];
            }
        }
        return outQ;
    }

    findNodeinAST(nodeId, analysisDefinition) {
        let flattenedAnalysisDefinition = this.flattenAnalysisDefinition(analysisDefinition);
        return flattenedAnalysisDefinition.find((n) => n.id === nodeId);
    }

    getNodeUrl(node) {
        let token = this.authService.token();
        if (this.workspace) {
            // eslint-disable-next-line max-len
            const nodeId = node.id ? node.id : node;
            const labNode = this.nodes.get(nodeId);
            const analysisId = labNode.analysisId;
            if (labNode.type === 'projectSrc') {
                return this.projectService.getProjectLayerURL({
                    id: labNode.projId
                }, {
                    token: token
                });
            }
            return `${this.tileServer}/analysis/${analysisId}/{z}/{x}/{y}` +
                    `?token=${token}&node=${nodeId}&tag=${new Date().getTime()}`;
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
        const layerOptions = {maxZoom: 30};
        if (this.previewLayers) {
            this.previewLayers.forEach(l => l.remove());
        }
        if (this.previewData.constructor === Array) {
            let url0 = this.getNodeUrl(this.previewData[0]);
            let url1 = this.getNodeUrl(this.previewData[1]);
            if (url0 && url1) {
                this.previewLayers = [
                    L.tileLayer(url0, layerOptions),
                    L.tileLayer(url1, layerOptions)
                ];
            }
        } else {
            let url0 = this.getNodeUrl(this.previewData);
            if (url0) {
                this.previewLayers = [L.tileLayer(url0, layerOptions)];
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
        // TODO adjust lab diagram center so when the preview opens,
        //      the center of the lab stays at the center of the div
        let defaultSplit = 40;
        if (!this.workspace) {
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
                            const analysisId = this.nodes.get(this.previewData).analysisId;
                            let s = this.analysisService.generateSourcesFromAST(
                                _.find(this.workspace.analyses, (a) => a.id === analysisId)
                            );
                            let firstSourceId = Object.keys(s)[0];
                            this.projectService.fetchProject(s[firstSourceId].projId).then(p => {
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
        return !Array.isArray(this.previewData) && this.previewData;
    }

    onNodeClose(side) {
        this.selectNode(this.previewData[side === 'left' ? 1 : 0]);
    }

    onLeftSelect(id) {
        this.compareNodes([
            id,
            this.previewData[1]
        ]);
    }

    onRightSelect(id) {
        this.compareNodes([
            this.previewData[0],
            id
        ]);
    }

    onSingleSelect(id) {
        this.previewData = id;
        this.selectNode(id);
    }

    onLeftClose() {
        this.selectNode(this.previewData[1]);
    }

    onRightClose() {
        this.selectNode(this.previewData[0]);
    }

    onSingleClose() {
        this.onPreviewClose();
    }

    onCompareClick() {
        if (!Array.isArray(this.previewData)) {
            this.compareNodes([this.previewData, this.previewData]);
        } else {
            this.selectNode(this.previewData[0]);
        }
    }
}

export default angular.module('pages.lab.workspace', [])
    .controller('LabWorkspaceController', LabWorkspaceController);

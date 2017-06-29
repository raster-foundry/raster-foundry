/* global L */

export default class LabRunController {
    constructor( // eslint-disable-line max-params
        $scope, $timeout, $element, authService, $uibModal, mapService, projectService,
        mapUtilsService, toolService, APP_CONFIG) {
        'ngInject';
        this.$scope = $scope;
        this.$parent = $scope.$parent.$ctrl;
        this.$timeout = $timeout;
        this.$element = $element;
        this.$uibModal = $uibModal;
        this.authService = authService;
        this.projectService = projectService;
        this.mapUtilsService = mapUtilsService;
        this.toolService = toolService;
        this.getMap = () => mapService.getMap('lab-run-preview');
        this.tileServer = `${APP_CONFIG.tileServerLocation}`;
    }

    $onInit() {
        this.$parent.toolRequest.then(tool => {
            this.tool = tool;
            // This will be a call to the API
            this.sources = this.generateSourcesFromTool();
            this.toolRun = this.generateToolRun();
            this.generatedPreview = false;
        });
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

    onParameterChange() {
        if (this.isShowingPreview) {
            if (this.generatedPreview) {
                this.updatePreviewLayers();
            } else {
                this.showPreview(this.previewData);
            }
        }
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
                    L.tileLayer(url0),
                    L.tileLayer(url1)
                ];
            }
        } else {
            let url0 = this.getNodeUrl(this.previewData);
            if (url0) {
                this.previewLayers = [L.tileLayer(url0)];
            }
        }
    }

    addSideBySide() {
        if (!this.sideBySideControl) {
            this.sideBySideControl =
                L.control.sideBySide(this.previewLayers[0], this.previewLayers[1]);
        } else {
            this.sideBySideControl.setLeftLayers(this.previewLayers[0]);
            this.sideBySideControl.setRightLayers(this.previewLayers[1]);
        }

        if (!this.sideBySideAdded) {
            this.sideBySideAdded = true;
            this.getMap().then((m) => {
                this.sideBySideControl.addTo(m.map);
            });
        }
    }

    showPreview(data) {
        this.isShowingPreview = true;
        if (data) {
            this.previewData = data;
            this.createPreviewLayers();
            if (this.previewLayers) {
                this.generatedPreview = true;
                this.getMap().then(m => {
                    this.previewLayers.forEach(l => l.addTo(m.map));
                    if (data.constructor === Array) {
                        this.addSideBySide();
                    } else if (!this.isComparing && this.sideBySideAdded) {
                        this.sideBySideControl.remove();
                        this.sideBySideAdded = false;
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
        this.toolService.createToolRun(this.toolRun).then(tr => {
            this.lastToolRun = tr;
        });
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
            this.onParameterChange();
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
}

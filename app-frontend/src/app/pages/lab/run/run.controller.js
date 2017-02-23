/* global L */

export default class LabRunController {
    constructor( // eslint-disable-line max-params
        $scope, $timeout, $element, authService, $uibModal, mapService, projectService) {
        'ngInject';
        this.$scope = $scope;
        this.$timeout = $timeout;
        this.$element = $element;
        this.$uibModal = $uibModal;
        this.authService = authService;
        this.projectService = projectService;
        this.getMap = () => mapService.getMap('lab-run-preview');
    }

    $onInit() {
        this.inputs = [false, false];
        this.inputParameters = [{
            bands: {
                nir: '5',
                red: '4'
            }
        }, {
            bands: {
                nir: '5',
                red: '4'
            }
        }];
        this.initControls();
        this.sceneList = [];
        this.generatedPreview = false;
    }

    initControls() {
        this.thresholds = {};

        this.thresholds.before = 0.1;
        this.thresholds.after = 0.1;

        this.reclassifyBeforeThreshold = {
            options: {
                floor: -1,
                ceil: 1,
                step: 0.05,
                precision: 2,
                onChange: this.onReclassifyBeforeThresholdChange.bind(this)
            }
        };
        this.reclassifyAfterThreshold = {
            options: {
                floor: -1,
                ceil: 1,
                step: 0.05,
                precision: 2,
                onChange: this.onReclassifyAfterThresholdChange.bind(this)
            }
        };
    }

    onReclassifyBeforeThresholdChange(id, val) {
        this.thresholds.before = val;
        this.onParameterChange();
    }

    onReclassifyAfterThresholdChange(id, val) {
        this.thresholds.after = val;
        this.onParameterChange();
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
        if (this.inputs.length === 2 && this.inputs[0].id && this.inputs[1].id) {
            if (node.tag.startsWith('input')) {
                let tag = new Date().getTime();
                let inputNum = node.tag.split('_')[1];
                return `/tiles/${this.inputs[inputNum].id}/{z}/{x}/{y}/` +
                       `?tag=${tag}&token=${token}`;
            }
            let base =
                '/tiles/tools/dfac6307-b5ef-43f7-beda-b9f208bb7726/ndvi-diff-tool/{z}/{x}/{y}';
            let lc80 = `LC8_0=${this.inputs[0].id}`;
            let lc81 = `LC8_1=${this.inputs[1].id}`;
            let part = `part=${node.tag}`;
            let class0 = `class0=${this.thresholds.before.toFixed(2)}:0;99999999:1.0`;
            let class1 = `class1=${this.thresholds.after.toFixed(2)}:0;99999999:1.0`;
            let cm =
                node.part === 'final' ? 'cm=-0.01:-16777216;0.01:0;1000000000:16711680' : '';
            let params = `${lc80}&${lc81}&${part}&${cm}&${class0}&${class1}`;
            return `${base}?${params}&token=${token}`;
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
                            this.fitProjectExtent(this.inputs[1]);
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
            m.fitProjectExtent(project);
        });
    }

    closePreview() {
        this.isShowingPreview = false;
    }

    selectProjectModal(src) {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfSelectProjectModal',
            resolve: {
                project: () => this.inputs[src],
                content: () => ({
                    title: 'Select a project'
                })
            }
        });

        this.activeModal.result.then(p => {
            this.inputs[src] = p;
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
                component: 'rfPublishModal',
                resolve: {
                    tileUrl: () => this.projectService.getBaseURL() + tileUrl,
                    noDownload: () => true
                }
            });
        }
        return this.activeModal;
    }
}

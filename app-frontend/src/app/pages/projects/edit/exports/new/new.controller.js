/* globals BUILDCONFIG */

export default class NewExportController {
    constructor(
        $scope, $state, $timeout,
        projectService, analysisService, mapService,
        projectEditService, exportService, authService,
        modalService
    ) {
        'ngInject';
        $scope.autoInject(this, arguments);

        this.$parent = this.$scope.$parent.$ctrl;

        this.availableResolutions = this.exportService.getAvailableResolutions();
        this.availableTargets = this.exportService.getAvailableTargets();
        this.availableProcessingOptions = this.projectService.availableProcessingOptionsThin;

        this.getMap = () => mapService.getMap('edit');
    }

    $onInit() {
        // Initial visible state of the output parameters pane
        this.showParameters = false;

        // @TODO: this can be removed from both here and the template when the export target
        // feature is implemented
        this.enableExportTargets = true;

        // @TODO: this can be removed from both here and the template when the export option
        // previews are implemented
        this.showPreviewImages = false;

        // Export defaults
        this.exportOptions = {
            resolution: 9,
            crop: false,
            raw: false
        };

        // draw options
        this.drawOptions = {
            areaType: 'export',
            requirePolygons: false
        };

        this.exportTargetURI = '';
        this.exportProcessingOption = this.getDefaultProcessingOption();
        this.exportTarget = this.getDefaultTarget();

        this.projectEditService.fetchCurrentProject().then(project => {
            this.project = project;
        });

        this.$scope.$on('$destroy', this.$onDestroy.bind(this));
    }

    $onDestroy() {
        this.getMap().then((mapWrapper) => {
            mapWrapper.deleteLayers('Export Area');
        });
    }

    getDefaultTarget() {
        return this.availableTargets.find(t => t.default) ||
               this.availableTargets[0];
    }


    getDefaultProcessingOption() {
        return this.availableProcessingOptions.find(o => o.default) ||
               this.availableProcessingOptions[0];
    }

    getCurrentResolution() {
        const resolutionValue = this.exportOptions.resolution;
        return this.availableResolutions
            .find(r => r.value === resolutionValue);
    }

    getCurrentTarget() {
        return this.exportTarget;
    }

    getCurrentProcessingOption() {
        const option = this.exportProcessingOption;
        return this.availableProcessingOptions
            .find(o => o.value === option.value);
    }


    getExportOptions(options = {}) {
        return Object.assign(
            this.exportOptions,
            this.getCurrentProcessingOption().exportOptions,
            this.mask ? {mask: this.mask} : {},
            options
        );
    }

    isCurrentProcessingOption(option) {
        return this.getCurrentProcessingOption().value === option.value;
    }

    shouldShowProcessingParams(option) {
        return this.isCurrentProcessingOption(option) &&
               option.templateId &&
               !this.isAnalysis;
    }

    shouldShowTargetParams() {
        return this.getCurrentTarget().value === 'externalS3';
    }

    toggleParameters() {
        this.showParameters = !this.showParameters;
    }

    updateResolution(level) {
        this.exportOptions.resolution = level;
    }

    updateTarget(target) {
        if (target.value === 'dropbox') {
            let hasDropbox = this.authService.user.dropboxCredential &&
                this.authService.user.dropboxCredential.length;
            if (hasDropbox) {
                this.exportTarget = target;
                let appName = BUILDCONFIG.APP_NAME.toLowerCase().replace(' ', '-');
                this.exportOptions.source = `dropbox:///${appName}/analyses/${this.analysisId}`;
            } else {
                this.displayDropboxModal();
            }
        } else {
            this.exportTarget = target;
        }
    }

    displayDropboxModal() {
        this.modalService.open({
            component: 'rfConfirmationModal',
            resolve: {
                title: () => 'You don\'t have Dropbox credential set',
                content: () => 'Go to your API connections page and set one?',
                confirmText: () => 'Add Dropbox credential',
                cancelText: () => 'Cancel'
            }
        }).result.then((resp) => {
            this.$state.go('user.settings.connections');
        });
    }

    handleOptionChange(state, option) {
        if (state) {
            this.exportProcessingOption = option;
            this.clearAnalysisSettings();
            if (option.templateId) {
                this.loadTemplate(option.templateId);
            }
        }
    }

    onOutputProcessingChange(option) {
        this.exportProcessingOption = option;
        if (!option.default) {
            this.exportOptions = Object.assign(this.exportOptions, option.exportOptions);
        }
    }

    clearAnalysisSettings() {
        this.templateRequest = null;
        this.isAnalysis = false;
        this.currentAnalysis = null;
        this.currentAnalysisSources = null;
        this.currentAnalysis = null;
    }

    loadTemplate(templateId) {
        this.isAnalysis = true;
        this.currentAnalysisSources = null;
        this.templateRequest = this.analysisService.getTemplate(templateId);
        this.templateRequest
            .then(t => {
                this.currentTemplate = t;
                this.currentAnalysisSources = this.analysisService.generateSourcesFromAST(t);
                this.currentAnalysis = this.analysisService.generateAnalysis(t);
                this.isAnalysis = false;
            });
    }

    finalizeExportOptions() {
        if (this.currentAnalysisSources) {
            Object.values(this.currentAnalysisSources).forEach(src => {
                this.analysisService.updateAnalysisSource(
                    this.currentAnalysis,
                    src.id,
                    this.project.id,
                    src.band
                );
            });
        }
        if (this.getCurrentTarget().value === 'externalS3') {
            this.exportOptions.source = this.exportTargetURI;
        } else if (this.getCurrentTarget().value === 'dropbox') {
            let appName = BUILDCONFIG.APP_NAME.toLowerCase().replace(' ', '-');
            this.exportOptions.source = `dropbox:///${appName}/projects/${this.project.id}`;
        }
    }

    validate() {
        let validationState = true;
        if (this.getCurrentTarget().value === 'externalS3') {
            validationState = validationState && this.exportTargetURI;
        }
        return validationState;
    }

    startExport() {
        this.isExporting = true;
        this.finalizeExportOptions();
        if (this.currentAnalysis) {
            this.createAnalysisExport();
        } else {
            this.createBasicExport();
        }
    }

    createAnalysisExport() {
        this.analysisService
            .createAnalysis(this.currentAnalysis)
            .then(tr => {
                this.projectService
                    .export(
                        this.project, {
                            analysisId: tr.id
                        },
                        this.getExportOptions()
                    )
                    .finally(() => {
                        this.finishExport();
                    });
            });
    }

    createBasicExport() {
        this.projectService
            .export(this.project,
                    {exportType: this.getCurrentTarget().value === 'dropbox' ? 'Dropbox' : 'S3' },
                    this.getExportOptions())
            .finally(() => {
                this.finishExport();
            });
    }

    finishExport() {
        // This timeout is intended as a rough feedback mechanism
        // A slight delay ensures the state change of the button
        // which indicates a process is beginning is noticed
        this.$timeout(() => {
            this.$parent.populateExportList();
            this.$state.go('projects.edit.exports');
        }, 500);
    }

    onDrawSave(multipolygon) {
        this.drawing = false;
        this.mask = multipolygon.geom;
        if (multipolygon) {
            let exportAreaLayer = L.geoJSON(this.mask, {
                style: () => {
                    return {
                        weight: 2,
                        fillOpacity: 0.2
                    };
                }
            });
            this.getMap().then((mapWrapper) => {
                mapWrapper.setLayer('Export Area', exportAreaLayer, true);
            });
        } else {
            this.getMap().then((mapWrapper) => {
                mapWrapper.deleteLayers('Export Area');
            });
        }
    }

    onDrawCancel() {
        this.drawing = false;
        this.getMap().then((mapWrapper) => {
            mapWrapper.showLayers('Export Area', true);
        });
    }

    startDrawing() {
        this.drawing = true;
        this.getMap().then((mapWrapper) => {
            mapWrapper.hideLayers('Export Area', false);
        });
    }
}

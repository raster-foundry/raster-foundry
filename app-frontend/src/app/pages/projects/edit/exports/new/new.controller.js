/* globals BUILDCONFIG */
const availableResolutions = [
    {
        label: '~300m',
        value: 9
    },
    {
        label: '~150m',
        value: 10
    },
    {
        label: '~75m',
        value: 11
    },
    {
        label: '~38m',
        value: 12
    },
    {
        label: '~19m',
        value: 13
    },
    {
        label: '~10m',
        value: 14
    },
    {
        label: '~5m',
        value: 15
    },
    {
        label: '~2m',
        value: 16
    },
    {
        label: '~1m',
        value: 17
    },
    {
        label: '~0.5m',
        value: 18
    },
    {
        label: '~0.3m',
        value: 19
    }
];

const availableTargets = [
    {
        label: 'Download',
        value: 'internalS3',
        default: true
    }, {
        label: 'S3 Bucket',
        value: 'externalS3'
    }, {
        label: 'Dropbox',
        value: 'dropbox'
    }
];

export default class NewExportController {
    constructor(
        $scope, $state, $timeout,
        projectService, analysisService, mapService,
        projectEditService
    ) {
        'ngInject';
        this.$scope = $scope;
        this.$parent = this.$scope.$parent.$ctrl;
        this.$state = $state;
        this.$timeout = $timeout;
        this.projectService = projectService;
        this.projectEditService = projectEditService;
        this.analysisService = analysisService;
        this.availableResolutions = availableResolutions;
        this.availableTargets = availableTargets;
        this.availableProcessingOptions = this.projectService.availableProcessingOptions;
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
            stitch: true,
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
        this.exportTarget = target;
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
            this.exportOptions.source = `dropbox:///Apps/${appName}/${this.project.id}`;
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

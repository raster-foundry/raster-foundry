const availableResolutions = [
    {
        label: '300m',
        value: 9
    },
    {
        label: '75m',
        value: 11
    },
    {
        label: '20m',
        value: 13
    },
    {
        label: '5m',
        value: 15
    },
    {
        label: '1m',
        value: 17
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

export default class ExportController {
    constructor($scope, $state, $timeout, projectService, toolService, mapService) {
        'ngInject';
        this.$scope = $scope;
        this.$parent = this.$scope.$parent.$ctrl;
        this.$state = $state;
        this.$timeout = $timeout;
        this.projectService = projectService;
        this.toolService = toolService;
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
            stitch: false,
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

        this.$parent.fetchProject().then(project => {
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
               option.toolId &&
               !this.isLoadingTool;
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
            this.clearToolSettings();
            if (option.toolId) {
                this.loadTool(option.toolId);
            }
        }
    }

    clearToolSettings() {
        this.toolRequest = null;
        this.isLoadingTool = false;
        this.currentTool = null;
        this.currentToolSources = null;
        this.currentToolRun = null;
    }

    loadTool(toolId) {
        this.isLoadingTool = true;
        this.currentToolSources = null;
        this.toolRequest = this.toolService.get(toolId);
        this.toolRequest
            .then(t => {
                this.currentTool = t;
                this.currentToolSources = this.toolService.generateSourcesFromTool(t);
                this.currentToolRun = this.toolService.generateToolRun(t);
                this.initToolRunSources();
                this.isLoadingTool = false;
            });
    }

    initToolRunSources() {
        if (this.currentToolRun) {
            let sources = this.currentToolRun.executionParameters.sources;
            for (let source in sources) {
                if (sources.hasOwnProperty(source)) {
                    sources[source].type = 'project';
                    sources[source].id = this.project.id;
                }
            }
        }
    }

    finalizeExportOptions() {
        if (this.getCurrentTarget().value === 'externalS3') {
            this.exportOptions.source = this.exportTargetURI;
        } else if (this.getCurrentTarget().value === 'dropbox') {
            this.exportOptions.source = `dropbox:///${this.project.id}`;
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
        if (this.currentToolRun) {
            this.createToolRunExport();
        } else {
            this.createBasicExport();
        }
    }

    createToolRunExport() {
        this.toolService
            .createToolRun(this.currentToolRun)
            .then(tr => {
                this.projectService
                    .export(
                        this.project, {
                            toolRunId: tr.id
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
            this.$state.go('projects.edit');
        }, 500);
    }

    onDrawSave(multipolygon) {
        this.drawing = false;
        this.mask = multipolygon;
        if (multipolygon) {
            let exportAreaLayer = L.geoJSON(this.mask.geom, {
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

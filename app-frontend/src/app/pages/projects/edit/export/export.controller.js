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

export default class ExportController {
    constructor($scope, $state, $timeout, projectService, toolService) {
        'ngInject';
        this.$scope = $scope;
        this.$parent = this.$scope.$parent.$ctrl;
        this.$state = $state;
        this.$timeout = $timeout;
        this.projectService = projectService;
        this.toolService = toolService;
        this.availableResolutions = availableResolutions;
        this.availableProcessingOptions = this.projectService.availableProcessingOptions;
    }

    $onInit() {
        // Initial visible state of the output parameters pane
        this.showParameters = false;

        // @TODO: this can be removed from both here and the template when the export target
        // feature is implemented
        this.enableExportTargets = false;

        // @TODO: this can be removed from both here and the template when the export cropping
        // feature is implemented
        this.enableExportCropping = false;

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

        this.exportProcessingOption = this.getDefaultProcessingOption();
        this.$parent.fetchProject().then(project => {
            this.project = project;
        });
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

    getCurrentProcessingOption() {
        const option = this.exportProcessingOption;
        return this.availableProcessingOptions
            .find(o => o.value === option.value);
    }

    getExportOptions(options = {}) {
        return Object.assign(
            this.exportOptions,
            this.getCurrentProcessingOption().exportOptions,
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

    toggleParameters() {
        this.showParameters = !this.showParameters;
    }

    updateResolution(level) {
        this.exportOptions.resolution = level;
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

    startExport() {
        this.isExporting = true;
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
            .export(this.project, {}, this.getExportOptions())
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
}

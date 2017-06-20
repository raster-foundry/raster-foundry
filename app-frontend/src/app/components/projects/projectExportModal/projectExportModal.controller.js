export default class ProjectExportModalController {
    constructor(projectService) {
        'ngInject';
        this.projectService = projectService;
    }

    $onInit() {
        this.minZoom = 1;
        this.maxZoom = 30;
        this.projectId = this.resolve.project.id;
        this.zoom = this.resolve.zoom;
        this.exportType = 'S3';
        this.exportTypes = [
            {active: true, label: 'S3'},
            {active: false, label: 'Dropbox'}
        ];
        this.exportSuccess = false;
        this.exportFailure = false;
        this.zoomSlider = {
            options: {
                floor: 1,
                ceil: 22,
                step: 1,
                precision: 1
            }
        };
    }

    onExportTypeChange(newExportType) {
        let newLabel = newExportType.label;
        this.exportTypes.forEach(exportType => {
            if (exportType.label === newLabel) {
                exportType.active = true;
                this.exportType = exportType;
            } else {
                exportType.active = false;
            }
        });
    }

    exportNotStarted() {
        return !this.exportSuccess && !this.exportFailure;
    }

    createExport() {
        let extraOptions = this.exportType.label === 'Dropbox' ?
            { source: `dropbox:///raster-foundry/${this.projectId}.tif` } :
            {};
        this.projectService
            .export(this.projectId, this.zoom, this.exportType.label, extraOptions)
            .then(
                () => {
                    // @TODO: should we do something with the export object that we get back here?
                    this.exportSuccess = true;
                },
                () => {
                    this.exportFailure = true;
                }
            );
    }
}

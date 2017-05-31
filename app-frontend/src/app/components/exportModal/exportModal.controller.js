export default class ExportModalController {
    constructor(projectService) {
        'ngInject';
        this.projectService = projectService;
    }

    $onInit() {
        this.minZoom = 1;
        this.maxZoom = 30;
        this.projectId = this.resolve.project.id;
        this.zoom = this.resolve.zoom;
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

    exportNotStarted() {
        return !this.exportSuccess && !this.exportFailure;
    }

    createExport() {
        this.projectService
            .export(this.projectId, this.zoom)
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

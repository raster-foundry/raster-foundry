export default class ExportDownloadModalController {
    constructor($log, $state, exportService) {
        'ngInject';
        this.$state = $state;
        this.exportService = exportService;
    }

    $onInit() {
        this.isLoading = false;
        this.isError = false;
        this.export = this.resolve.export;
        this.fetchExportFiles();
    }

    fetchExportFiles() {
        this.isLoading = true;
        this.exportService.getFiles(this.export).then(f => {
            // @TODO: the extra filter can be removed once we aren't getting this extra file
            // see issue #2090
            this.exportFiles = f.filter(path => path.indexOf('RFUploadAccessTestFile') < 0);
            this.isLoading = false;
        }, () => {
            this.isError = true;
            this.isLoading = false;
        });
    }
}

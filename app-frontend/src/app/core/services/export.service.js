export default (app) => {
    class ExportService {
        constructor($resource, $q, authService) {
            'ngInject';
            this.$q = $q;
            this.authService = authService;

            this.Export = $resource(
                '/api/exports/:id/', {
                    id: '@properties.id'
                }, {
                    getFiles: {
                        isArray: true,
                        url: '/api/exports/:exportId/files',
                        params: {
                            exportId: '@exportId'
                        }
                    }
                }
            );
        }

        getFiles(exportObject) {
            const token = this.authService.token();
            return this.$q((resolve) => {
                this.Export.getFiles({ exportId: exportObject.id })
                    .then(files => {
                        resolve(files.map(f => {
                            return `/api/exports/${exportObject.id}/files/${f}?token=${token}`;
                        }));
                    });
            });
        }
    }


    app.service('exportService', ExportService);
};

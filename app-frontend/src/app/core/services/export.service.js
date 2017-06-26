export default (app) => {
    class ExportService {
        constructor($resource) {
            'ngInject';

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
            return this.Export.getFiles({ exportId: exportObject.id }).$promise;
        }
    }


    app.service('exportService', ExportService);
};

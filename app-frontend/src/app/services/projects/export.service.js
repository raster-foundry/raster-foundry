/* globals BUILDCONFIG */

export default (app) => {
    class ExportService {
        constructor($resource, $q, authService) {
            'ngInject';
            this.$q = $q;
            this.authService = authService;

            this.Export = $resource(
                `${BUILDCONFIG.API_HOST}/api/exports/:id/`, {
                    id: '@properties.id'
                }, {
                    query: {
                        method: 'GET',
                        cache: false,
                        url: `${BUILDCONFIG.API_HOST}/api/exports`
                    },
                    getFiles: {
                        isArray: true,
                        url: `${BUILDCONFIG.API_HOST}/api/exports/:exportId/files`,
                        params: {
                            exportId: '@exportId'
                        }
                    },
                    createExport: {
                        method: 'POST',
                        url: `${BUILDCONFIG.API_HOST}/api/exports/`
                    }
                }
            );
        }

        query(params = {}) {
            return this.Export.query(params).$promise;
        }

        getFiles(exportObject) {
            const token = this.authService.token();
            return this.$q((resolve) => {
                this.Export
                    .getFiles({ exportId: exportObject.id }).$promise
                    .then(files => {
                        resolve(files.map(f => {
                            //eslint-disable-next-line
                            return `${BUILDCONFIG.API_HOST}/api/exports/${exportObject.id}/files/${f}?token=${token}`;
                        }));
                    });
            });
        }

        exportLabNode(toolRunId, settings = {}, options = {}) {
            const defaultOptions = {
                resolution: 9,
                crop: false
            };

            const finalOptions = Object.assign(defaultOptions, options);

            const defaultSettings = {
                toolRunId,
                exportStatus: 'NOTEXPORTED',
                exportType: 'S3',
                visibility: 'PRIVATE',
                exportOptions: finalOptions
            };

            const finalSettings = Object.assign(defaultSettings, settings);

            const userRequest = this.authService.getCurrentUser();

            return userRequest.then(
                (user) => {
                    return this.Export.createExport(
                        Object.assign(finalSettings, {
                            owner: user.id
                        })
                    ).$promise;
                },
                (error) => {
                    return error;
                }
            );
        }

        getAvailableResolutions() {
            return [
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
        }

        getAvailableTargets(includeS3 = true) {
            let targets = [
                {
                    label: 'Download',
                    value: 'internalS3',
                    default: true
                }, {
                    label: 'Dropbox',
                    value: 'dropbox'
                }
            ];

            if (includeS3) {
                targets.push({
                    label: 'S3 Bucket',
                    value: 'externalS3'
                });
            }

            return targets;
        }
    }


    app.service('exportService', ExportService);
};

/* globals BUILDCONFIG */

export default (app) => {
    class UploadService {
        constructor($resource) {
            'ngInject';
            this.Upload = $resource(
                `${BUILDCONFIG.API_HOST}/api/uploads/:id`, {
                    id: '@id'
                }, {
                    query: {
                        method: 'GET',
                        cache: false
                    },
                    create: {
                        method: 'POST'
                    },
                    credentials: {
                        method: 'GET',
                        url: `${BUILDCONFIG.API_HOST}/api/uploads/:id/credentials`
                    },
                    update: {
                        method: 'PUT'
                    }
                }
            );
        }

        query(params = {}) {
            return this.Upload.query(params).$promise;
        }

        create(data) {
            return this.Upload.create(data).$promise;
        }

        credentials(upload) {
            return this.Upload.credentials({ id: upload.id }).$promise;
        }

        update(data) {
            return this.Upload.update(data).$promise;
        }
    }

    app.service('uploadService', UploadService);
};

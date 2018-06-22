/* globals BUILDCONFIG */
export default (app) => {
    class ShapesService {
        constructor($resource) {
            this.shapeApi = $resource(`${BUILDCONFIG.API_HOST}/api/shapes/:id`, {
                id: '@properties.id'
            }, {
                query: {
                    method: 'GET',
                    cache: false
                },
                get: {
                    method: 'GET',
                    cache: false
                },
                delete: {
                    method: 'DELETE'
                },
                create: {
                    method: 'POST',
                    isArray: true
                }
            });
        }

        query(params = {}) {
            return this.shapeApi.query(params).$promise;
        }

        fetchShapes(params) {
            return this.shapeApi.get(params).$promise;
        }

        deleteShape(params) {
            return this.shapeApi.delete(params).$promise;
        }

        createShape(shape) {
            return this.shapeApi.create(shape).$promise;
        }
    }

    app.service('shapesService', ShapesService);
};

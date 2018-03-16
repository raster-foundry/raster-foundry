/* globals BUILDCONFIG */
export default (app) => {
    class ShapesService {
        constructor($resource) {
            this.shapeApi = $resource(`${BUILDCONFIG.API_HOST}/api/shapes/:id`, {id: '@id'}, {
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

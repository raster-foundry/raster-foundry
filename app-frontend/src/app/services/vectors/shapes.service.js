/* globals BUILDCONFIG */
export default app => {
    class ShapesService {
        constructor(uuid4, $resource) {
            this.shapeApi = $resource(
                `${BUILDCONFIG.API_HOST}/api/shapes/:id`,
                {
                    id: '@properties.id'
                },
                {
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
                }
            );
            this.uuid4 = uuid4;
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

        generateFeatureCollection(geometryArray, name = '') {
            return {
                type: 'FeatureCollection',
                features: geometryArray.map(geom => this.generateFeature(geom, name))
            };
        }

        generateFeature(geometry, name = '') {
            let result = {
                type: 'Feature',
                properties: {},
                geometry
            };

            if (name.length) {
                result.id = this.uuid4.generate();
                result.properties = { name };
            }

            return result;
        }
    }

    app.service('shapesService', ShapesService);
};

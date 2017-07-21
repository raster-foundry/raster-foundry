/* globals BUILDCONFIG */

export default (app) => {
    class SceneService {
        constructor($resource) {
            'ngInject';

            this.Scene = $resource(
                `${BUILDCONFIG.API_HOST}/api/scenes/:id/`, {
                    id: '@properties.id'
                }, {
                    query: {
                        method: 'GET',
                        cache: false
                    },
                    get: {
                        method: 'GET',
                        cache: false
                    }
                }
            );
        }

        query(params = {}) {
            let validParams = Object.assign(
                params,
                {minCloudCover: params.minCloudCover ? params.minCloudCover : 0}
            );
            return this.Scene.query(validParams).$promise;
        }

        deleteScene(scene) {
            return this.Scene.delete({id: scene.id}).$promise;
        }

        getSceneBounds(scene) {
            let boundsGeoJson = L.geoJSON();
            boundsGeoJson.addData(scene.dataFootprint);
            return boundsGeoJson.getBounds();
        }

        /**
        * Generate a styled GeoJSON footprint, suitable for placing on a map.
        * @param {Scene} scene For which to generate a GeoJSON footprint
        *
        * @returns {Object} GeoJSON footprint of scene.
        */
        getStyledFootprint(scene) {
            let styledGeojson = Object.assign({}, scene.dataFootprint, {
                properties: {
                    options: {
                        weight: 2,
                        fillOpacity: 0
                    }
                }
            });
            return styledGeojson;
        }

    }

    app.service('sceneService', SceneService);
};

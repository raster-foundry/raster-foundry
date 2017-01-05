export default (app) => {
    class ColorCorrectService {
        constructor($resource) {
            'ngInject';

            this.colorCorrect = $resource(
                '/api/projects/:projectId/mosaic/:sceneId/', {}, {
                    get: {
                        method: 'GET',
                        cache: false,
                        transformResponse: (data) => {
                            let parsedData = {};
                            if (data !== '') {
                                parsedData = angular.fromJson(data);
                            }
                            return Object.assign(this.getDefaultColorCorrection(), parsedData);
                        }
                    },
                    create: {
                        method: 'POST'
                    },
                    update: {
                        method: 'PUT'
                    }
                }
            );
        }

        /** Function to return default color correction
         *
         * @TODO: refactor to pull defaults from datasource
         *
         * @return {object} default color correction object
         */
        getDefaultColorCorrection() {
            return {
                redBand: 3,
                greenBand: 2,
                blueBand: 1,
                redGamma: 0.5,
                blueGamma: 0.5,
                greenGamma: 0.5,
                brightness: -6,
                contrast: 9,
                alpha: 0.4,
                beta: 13,
                min: 0,
                max: 20000,
                equalize: false
            };
        }

        /** Function to reset a scene's color correction with default
         *
         * @param {string} sceneId id for scene to reset color correction for
         * @param {string} projectId id for project that scene belongs to
         * @return {null} null
         */
        reset(sceneId, projectId) {
            return this.updateOrCreate(sceneId, projectId, this.getDefaultColorCorrection());
        }

        /** Function to obtain a scene's color correction for a given project
         *
         * @param {string} sceneId id for scene to retrieve color correction
         * @param {string} projectId id for project the scene's color correction belongs to
         * @return {Promise} promise with value for color correction
         */
        get(sceneId, projectId) {
            return this.colorCorrect.get(
                {sceneId: sceneId, projectId: projectId}
            ).$promise;
        }

        /** Function to update or create color correction for scene/project
         *
         * @TODO: Refactor once #880 is fixed and API is better
         *
         * @param {string} sceneId id for scene to update/create color correction
         * @param {string} projectId id for project the scene's color correction belongs to
         * @param {object} data json data to send as payload
         * @return {Promise} response with data
         */
        updateOrCreate(sceneId, projectId, data) {
            return this.colorCorrect.create(
                {sceneId: sceneId, projectId: projectId}, data
            ).$promise.then(() => {
                return data;
            });
        }

    }

    app.service('colorCorrectService', ColorCorrectService);
};

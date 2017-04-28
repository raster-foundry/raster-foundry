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

            this.bulkColorCorrect = $resource(
                '/api/projects/:projectId/mosaic/bulk-update-color-corrections/', {}, {
                    create: {
                        method: 'POST',
                        params: {
                            id: '@projectId'
                        }
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
                redGamma: null,
                blueGamma: null,
                greenGamma: null,
                brightness: null,
                contrast: null,
                alpha: null,
                beta: null,
                min: null,
                max: null,
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
            return this.update(sceneId, projectId, this.getDefaultColorCorrection());
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
         * @param {string} sceneId id for scene to update/create color correction
         * @param {string} projectId id for project the scene's color correction belongs to
         * @param {object} data json data to send as payload
         * @return {Promise} response with data
         */
        update(sceneId, projectId, data) {
            return this.colorCorrect.update(
                {sceneId: sceneId, projectId: projectId}, data
            ).$promise.then(() => {
                return data;
            });
        }

        /** Function to update or create color correction for multiple scenes
         *
         * @param {string} projectId id of current project
         * @param {string[]} sceneIds array of scenes to set color correction for
         * @param {object} data color-correction params to set for each scene.
         * If not provided, color corrections are returned to their defaults
         * @return {Promise} response with data
         */
        bulkUpdate(projectId, sceneIds, data) {
            const resolvedColorCorrection = data || this.getDefaultColorCorrection();
            const bulkData = sceneIds.map(s => {
                return {
                    sceneId: s,
                    params: resolvedColorCorrection
                };
            });
            return this.bulkColorCorrect.create(
                { projectId: projectId },
                { items: bulkData }
            ).$promise;
        }

    }

    app.service('colorCorrectService', ColorCorrectService);
};

/* globals BUILDCONFIG */

export default (app) => {
    class SceneService {
        constructor($resource, authService, projectService, uuid4) {
            'ngInject';
            this.authService = authService;
            this.projectService = projectService;
            this.uuid4 = uuid4;

            this.Scene = $resource(
                `${BUILDCONFIG.API_HOST}/api/scenes/:id/`, {
                    id: '@id'
                }, {
                    query: {
                        method: 'GET',
                        cache: false
                    },
                    get: {
                        method: 'GET',
                        cache: false
                    },
                    create: {
                        method: 'POST',
                        url: `${BUILDCONFIG.API_HOST}/api/scenes`,
                        params: {}
                    },
                    update: {
                        method: 'PUT',
                        cache: false
                    },
                    download: {
                        method: 'GET',
                        url: `${BUILDCONFIG.API_HOST}/api/scenes/:id/download`,
                        isArray: true,
                        params: {
                            id: '@id'
                        }
                    }
                }
            );
        }

        query(params = {}) {
            return this.Scene.query(params).$promise;
        }

        createCogScene(scene, datasource) {
            if (scene.location) {
                const sceneId = this.uuid4.generate();
                const sceneName = (() => {
                    const els = scene.location.split('/');
                    const els2 = els[els.length - 1].split('.');
                    return els2[0];
                })();
                const userP = this.authService.getCurrentUser();
                const sceneP = userP.then(user => {
                    return this.Scene.create({
                        id: sceneId,
                        organizationId: user.organizationId,
                        ingestSizeBytes: 0,
                        visibility: 'PRIVATE',
                        tags: scene.tags || [],
                        datasource: datasource.id,
                        sceneMetadata: scene.metadata || {},
                        name: sceneName,
                        owner: user.id,
                        metadataFiles: scene.metadataFiles || [],
                        images: [{
                            organizationId: user.organizationId,
                            rawDataBytes: 0,
                            visibility: 'PRIVATE',
                            filename: scene.location,
                            sourceUri: scene.location,
                            scene: sceneId,
                            imageMetadata: {},
                            owner: user.id,
                            resolutionMeters: 0,
                            metadataFiles: [],
                            bands: datasource.bands.map(b => {
                                const w = '' + Math.trunc(b.wavelength);
                                return Object.assign({}, b, {
                                    wavelength: [w, w]
                                });
                            })
                        }],
                        thumbnails: [],
                        ingestLocation: scene.location,
                        statusFields: {
                            thumbnailStatus: 'SUCCESS',
                            boundaryStatus: 'SUCCESS',
                            ingestStatus: 'INGESTED'
                        },
                        filterFields: scene.metadata || {},
                        sceneType: 'COG'
                    }).$promise;
                });
                if (scene.projectId) {
                    sceneP.then(newScene => {
                        this.projectService.addScenes(scene.projectId, [newScene.id]);
                    });
                }
                return sceneP;
            }
            return false;
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

        update(sceneParams = {}) {
            return this.Scene.update(sceneParams).$promise;
        }

        getDownloadableImages(scene) {
            return this.Scene.download({id: scene.id}).$promise;
        }
    }

    app.service('sceneService', SceneService);
};

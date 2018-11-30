/* globals BUILDCONFIG, btoa, Uint8Array */

export default (app) => {
    class SceneService {
        constructor($resource, $http, APP_CONFIG,
            authService, projectService, uuid4) {
            'ngInject';
            this.$http = $http;
            this.authService = authService;
            this.projectService = projectService;
            this.uuid4 = uuid4;

            this.tileServer = `${APP_CONFIG.tileServerLocation}`;

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
                    },
                    datasource: {
                        method: 'GET',
                        url: `${BUILDCONFIG.API_HOST}/api/scenes/:id/datasource`,
                        cache: true,
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

        datasource({id}) {
            return this.Scene.datasource({id: id}).$promise;
        }

        // set the default floor to 25 to brighten up images -- this was a fine value for
        // MODIS Terra scenes, but other datasources may need to pass a different parameter
        cogThumbnail(sceneId, token, width = 128, height = 128,
                     red = 0, green = 1, blue = 2, floor = 25) {
            return this.$http({
                method: 'GET',
                url: `${BUILDCONFIG.API_HOST}/api/scenes/${sceneId}/thumbnail`,
                headers: {
                    'Authorization': 'Bearer ' + token,
                    'Content-Type': 'arraybuffer'
                },
                responseType: 'arraybuffer',
                params: {
                    red,
                    green,
                    blue,
                    floor,
                    width,
                    height,
                    token
                }
            }).then(
                (response) => {
                    let arr = new Uint8Array(response.data);
                    let rawString = this.uint8ToString(arr);
                    return btoa(rawString);
                },
                (error) => {
                    return error;
                }
            );
        }

        uint8ToString(u8a) {
            const CHUNK_SZ = 0x8000;
            let result = [];
            for (let i = 0; i < u8a.length; i += CHUNK_SZ) {
                result.push(String.fromCharCode.apply(null, u8a.subarray(i, i + CHUNK_SZ)));
            }
            return result.join('');
        }

        getSceneLayerURL(scene, params) {
            let sceneId = typeof scene === 'object' ? scene.id : scene;
            let queryParams = params || {};
            let formattedParams = L.Util.getParamString(queryParams);
            return `${this.tileServer}/scenes/${sceneId}/{z}/{x}/{y}/${formattedParams}`;
        }
    }

    app.service('sceneService', SceneService);
};

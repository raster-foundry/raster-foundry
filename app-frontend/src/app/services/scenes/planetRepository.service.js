/* globals document */
import _ from 'lodash';

export default (app) => {
    class PlanetRepository {
        constructor($q, authService, planetLabsService, uploadService, modalService, $state) {
            this.$q = $q;
            this.authService = authService;
            this.planetLabsService = planetLabsService;
            this.uploadService = uploadService;
            this.modalService = modalService;
            this.$state = $state;
        }

        // returns Promise()
        initRepository() {
            return this.$q((resolve, reject) => {
                this.sources = [{
                    id: 'PSScene4Band',
                    uuid: 'e4d1b0a0-99ee-493d-8548-53df8e20d2aa',
                    name: 'PlanetScope - 4 band'
                }, {
                    id: 'REOrthoTile',
                    uuid: 'dd68e7eb-4055-4657-9cfb-bd82c0904f78',
                    name: 'RapidEye OrthoTiles'
                }];
                if (this.planetToken) {
                    resolve();
                } else {
                    this.authService.getCurrentUser().then((user) => {
                        if (user.planetCredential) {
                            this.planetToken = user.planetCredential;
                            resolve();
                        } else {
                            const modal = this.modalService.open({
                                component: 'rfConfirmationModal',
                                resolve: {
                                    title: () => 'You don\'t have a Planet API token set',
                                    content: () => 'Go to your API connections page and set one?',
                                    confirmText: () => 'Add Planet API Token',
                                    cancelText: () => 'Cancel'
                                }
                            });

                            modal.result.then(() => {
                                reject();
                                this.$state.go('settings.connections');
                            }, () => {
                                reject();
                            });
                        }
                    }, () => {
                        reject();
                    });
                }
            });
        }

        getFilters() {
            return [{
                param: 'datasource',
                label: 'Imagery Sources',
                type: 'searchSelect',
                getSources: this.getSources.bind(this)
            }, {
                params: {
                    min: 'minAcquisitionDatetime',
                    max: 'maxAcquisitionDatetime'
                },
                label: 'Date Range',
                type: 'daterange',
                default: 'The last month'
            }, {
                params: {
                    min: 'minCloudCover',
                    max: 'maxCloudCover'
                },
                label: 'Cloud Cover',
                type: 'slider',
                min: 0,
                max: 100,
                ticks: 10,
                step: 10,
                scale: 0.01
            }, {
                params: {
                    min: 'minSunElevation',
                    max: 'maxSunElevation'
                },
                label: 'Sun Elevation',
                type: 'slider',
                min: 0,
                max: 180,
                ticks: 30,
                step: 10,
                scale: 1
            }, {
                params: {
                    min: 'minSunAzimuth',
                    max: 'maxSunAzimuth'
                },
                label: 'Sun Azimuth',
                type: 'slider',
                min: 0,
                max: 360,
                ticks: 60,
                step: 10,
                scale: 1
            }];
        }

        getSources() {
            return this.$q((resolve) => {
                resolve(this.sources);
            });
        }


        /*
          Returns a function which fetches scenes

          Function chain:
          (filters) => (bbox) => () => Future(next page of scenes)
         */
        fetchScenes(filters) {
            const params = filters;
            // each bbox creates a new set of requests,
            const fetchForBbox = (bboxString) => {
                let scenePages = [];
                let nextLink = null;
                let fetchedScenes = 0;

                let bboxCoords = [];
                if (bboxString && bboxString.length) {
                    let coordsStrings = bboxString.split(',');
                    let coords = _.map(coordsStrings, str => parseFloat(str));
                    // Leaflet expects nested coordinate arrays
                    bboxCoords = [
                        [coords[1], coords[0]],
                        [coords[3], coords[2]]
                    ];
                }

                const bbox = L.latLngBounds(bboxCoords);

                const search = () => {
                    return this.$q((resolve, reject) => {
                        const startSearch = () => {
                            this.planetLabsService.filterScenes(
                                this.planetToken,
                                this.planetLabsService.constructRequestBody(params, bbox)
                            ).then((res) => {
                                if (res.status === 200) {
                                    fetchedScenes = res.data.features.length;
                                    scenePages = this.planetLabsService
                                        .planetFeatureToScene(res.data);
                                    let scenes = scenePages.shift();
                                    // eslint-disable-next-line
                                    nextLink = res.data.features.length === 250 && res.data._links._next;
                                    resolve({
                                        scenes,
                                        hasNext: !!nextLink || scenePages.length,
                                        count: nextLink ? `>${fetchedScenes}` : fetchedScenes
                                    });
                                } else {
                                    reject(`Unexpected planet API response code: ${res.status}`);
                                }
                            }, (err) => {
                                reject(err);
                                this.$log.log(err);
                            });
                        };
                        const continueSearch = () => {
                            this.planetLabsService.getFilteredScenesNextPage(
                                this.planetToken, nextLink
                            ).then((res) => {
                                if (res.status === 200) {
                                    scenePages = this.planetLabsService
                                        .planetFeatureToScene(res.data);
                                    fetchedScenes = fetchedScenes + res.data.features.length;
                                    let scenes = scenePages.shift();
                                    // eslint-disable-next-line
                                    nextLink = res.data.features.length === 250 && res.data._links._next;
                                    resolve({
                                        scenes,
                                        hasNext: !!nextLink || scenePages.length,
                                        count: nextLink ? `>${fetchedScenes}` : fetchedScenes
                                    });
                                }
                            }, (err) => {
                                reject(err);
                                this.$log.error(err);
                            });
                        };
                        if (nextLink) {
                            continueSearch();
                        } else {
                            startSearch();
                        }
                    });
                };

                return () => {
                    if (!scenePages.length && (nextLink === null || nextLink)) {
                        return search();
                    }
                    return this.$q((resolve, reject) => {
                        if (!scenePages.length) {
                            reject('No more scenes to fetch');
                        }
                        let scenes = scenePages.shift();
                        let hasNext = scenePages.length || !!nextLink;
                        resolve({
                            scenes,
                            hasNext,
                            count: nextLink ? `>${fetchedScenes}` : fetchedScenes
                        });
                    });
                };
            };

            return fetchForBbox;
        }

        getThumbnail(scene) {
            return this.$q((resolve, reject) => {
                this.planetLabsService.getThumbnail(
                    this.planetToken, scene.thumbnails[0].url
                ).then((response) => {
                    let thumbnail = `data:image/png;base64,${response}`;
                    this.trimThumbnail(thumbnail).then((trimmed) => {
                        resolve(trimmed);
                    }, (err) => {
                        reject(err);
                    });
                }, (err) => {
                    reject(err);
                });
            });
        }

        // assumes thumbnail is 256x256, which is true for planet thumbnails
        trimThumbnail(thumbnail) {
            return this.$q((resolve, reject) => {
                const width = 256;
                const height = 256;
                const canvas = document.createElement('canvas');
                canvas.width = width;
                canvas.height = height;
                const context = canvas.getContext('2d');
                const image = document.createElement('img');
                image.onload = () => {
                    context.drawImage(image, 0, 0);
                    const pixels = context.getImageData(0, 0, width, height);
                    let l = pixels.data.length;
                    let x = 0;
                    let y = 0;
                    const bound = {
                        top: null,
                        left: null,
                        right: null,
                        bottom: null
                    };
                    for (let i = 0; i < l; i += 4) {
                        if (pixels.data[i + 3] !== 0) {
                            x = i / 4 % width;
                            y = ~~(i / 4 / height);
                            if (bound.top === null) {
                                bound.top = y;
                            }
                            if (bound.bottom === null || bound.bottom < y) {
                                bound.bottom = y;
                            }
                            if (bound.left === null || x < bound.left) {
                                bound.left = x;
                            }
                            if (bound.right === null || bound.right < x) {
                                bound.right = x;
                            }
                        }
                    }
                    if (bound.top !== null) {
                        let trimHeight = bound.bottom - bound.top;
                        let trimWidth = bound.right - bound.left;
                        const trimmedData = context.getImageData(
                            bound.left, bound.top, trimWidth, trimHeight
                        );
                        const trimmedCanvas = document.createElement('canvas');
                        trimmedCanvas.width = trimWidth;
                        trimmedCanvas.height = trimHeight;
                        const trimmedContext = trimmedCanvas.getContext('2d');
                        trimmedContext.putImageData(trimmedData, 0, 0);
                        resolve(trimmedCanvas.toDataURL());
                    } else {
                        reject('empty image');
                    }
                };
                image.src = thumbnail;
            });
        }

        getPreview(scene) {
            return this.getThumbnail(scene);
        }

        getDatasource(scene) {
            return this.$q((resolve) => {
                let source = _.first(this.sources.filter((s) => s.name === scene.datasource));
                if (source) {
                    resolve(source.label);
                } else {
                    resolve(scene.datasource);
                }
            });
        }

        addToProject(projectId, scenes) {
            const planetSceneId = scene => `${scene.datasource}:${scene.id}`;
            const getDatasourceId =
                  datasource => _.first(_.filter(this.sources, s => s.id === datasource)).uuid;
            return this.$q((resolve, reject) => {
                this.authService.getCurrentUser().then(user => {
                    // create separate upload for each datasource
                    let sceneGroups = _.groupBy(scenes, (scene) => scene.datasource);
                    let datasourceIds = _.map(sceneGroups, (datasourceScenes, datasource) => {
                        let sceneIds = datasourceScenes.map(planetSceneId);
                        return {datasource, sceneIds};
                    });
                    let uploadPromises = datasourceIds.map(({datasource, sceneIds}) => {
                        let dsId = getDatasourceId(datasource);
                        let uploadObject = {
                            files: sceneIds,
                            fileType: 'GEOTIFF',
                            datasource: dsId,
                            uploadStatus: 'UPLOADED',
                            visibility: 'PRIVATE',
                            uploadType: 'PLANET',
                            organizationId: user.organizationId,
                            projectId,
                            metadata: {
                                planetKey: this.planetToken
                            }
                        };
                        return this.uploadService.create(uploadObject);
                    });
                    this.$q.all(uploadPromises).then(() => {
                        resolve(scenes.map(planetSceneId));
                    }, (err) => {
                        reject(err);
                    });
                });
            });
        }
    }

    app.service('PlanetRepository', PlanetRepository);
};

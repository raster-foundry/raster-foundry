/* global _*/

import {Map} from 'immutable';
export default (app) => {
    class RasterFoundryRepository {
        constructor(
            $q, $filter,
            authService, datasourceService, sceneService, thumbnailService,
            projectService
        ) {
            this.$q = $q;
            this.$filter = $filter;
            this.authService = authService;
            this.datasourceService = datasourceService;
            this.sceneService = sceneService;
            this.thumbnailService = thumbnailService;
            this.projectService = projectService;
            this.datasourceCache = new Map();
            this.previewOnMap = true;
            this.cogThumbnailCache = [];
            this.defaultRepository = true;
        }

        initRepository() {
            return this.$q((resolve) => {
                resolve();
            });
        }

        getFilters() {
            return [{
                param: 'datasource',
                label: 'Imagery Sources',
                type: 'search-select',
                getSources: this.getSources.bind(this)
            }, {
                params: {
                    min: 'minAcquisitionDatetime',
                    max: 'maxAcquisitionDatetime'
                },
                label: 'Date Range',
                type: 'daterange',
                default: 'None'
            }, {
                type: 'shape',
                label: 'Area of Interest',
                param: 'shape'
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
                scale: 1
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
            }, {
                param: 'ingested',
                label: 'Ingest Status',
                type: 'tag',
                options: [{
                    label: 'Show all',
                    value: null
                }, {
                    label: 'Uningested Only',
                    value: 'false'
                }, {
                    label: 'Ingested Only',
                    value: 'true'
                }]
            }, {
                param: 'owner',
                label: 'Owner',
                type: 'tag',
                options: [{
                    label: 'All',
                    value: null
                }, {
                    label: 'My Scenes',
                    value: this.authService.getProfile().sub
                }]
            }];
        }

        getSources() {
            let deferred = this.$q.defer();

            let promise = this.datasourceService.query({sort: 'name,asc'}).then((res) => {
                let pageCount = Math.ceil(res.count / res.pageSize);

                let promises = [promise].concat(_.times(pageCount, (idx) => {
                    return this.datasourceService
                        .query({sort: 'name,asc', page: idx + 1})
                        .then(resp => resp, error => error);
                }));

                this.$q.all(promises).then((reps) => {
                    deferred.resolve(_.flatMap(reps, r => r.results));
                }, error => deferred.reject(error));

                return res;
            }, (err) => deferred.reject(err));

            return deferred.promise;
        }

        /*
          Returns a function which fetches scenes

          Function chain:
          (filters) => (bbox) => () => Future(next page of scenes)
        */
        fetchScenes(filters, projectId) {
            if (filters.shape && typeof filters.shape === 'object') {
                filters.shape = filters.shape.id;
            }
            const params = Object.assign({}, filters);

            const fetchForBbox = (bbox) => {
                let hasNext = null;
                let page = 0;
                let requestTime = new Date().toISOString();

                return () => {
                    return this.$q((resolve, reject) => {
                        if (hasNext !== null && !hasNext) {
                            reject('No more scenes to fetch.');
                        }
                        this.sceneService.query(
                            Object.assign({
                                sort: 'acquisitionDatetime,desc',
                                pageSize: '20',
                                page,
                                bbox,
                                maxCreateDatetime: requestTime,
                                project: projectId
                            }, params)
                        ).then((response) => {
                            // We aren't supporting concurrent scene paged requests
                            page = page + 1;
                            hasNext = response.hasNext;
                            resolve({
                                scenes: response.results,
                                hasNext,
                                count: response.count >= 100 ?
                                    'at least 100' : this.$filter('number')(response.count)
                            });
                        }, (error) => {
                            reject({
                                error
                            });
                        });
                    });
                };
            };

            return fetchForBbox;
        }

        getThumbnail(scene) {
            return this.$q((resolve, reject) => {
                if (scene.thumbnails.length) {
                    resolve(this.thumbnailService.getBestFitUrl(scene.thumbnails, 75));
                } else {
                    reject();
                }
            });
        }

        getPreview(scene) {
            return this.$q((resolve, reject) => {
                if (scene.thumbnails.length) {
                    resolve(this.thumbnailService.getBestFitUrl(scene.thumbnails, 1000));
                } else {
                    reject();
                }
            });
        }

        getDatasource(scene) {
            return this.$q((resolve) => resolve(scene.datasource));
        }


        /*
          Returns a function which adds the given RF scenes to the project
         */
        addToProject(projectId, scenes) {
            return this.projectService.addScenes(projectId, scenes.map(scene => scene.id));
        }

        getScenePermissions(scene) {
            let result = [];
            if (scene) {
                result.push('download');
            }
            return result;
        }
    }

    app.service('RasterFoundryRepository', RasterFoundryRepository);
};

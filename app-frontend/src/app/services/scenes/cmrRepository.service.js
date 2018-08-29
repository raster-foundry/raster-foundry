// import _ from 'lodash';
import axios from 'axios';

export default (app) => {
    class CMRRepository {
        constructor($q, authService, uploadService) {
            this.$q = $q;
            this.previewOnMap = false;
            this.authService = authService;
            this.uploadService = uploadService;
        }

        initDatasources() {
            this.datasources = [{
                name: 'MYD09A1: MODIS/Aqua ',
                uuid: '755735945-9da5-47c3-8ae4-572b5e11205b',
                id: 'MYD09A1',
                uploadType: 'MODIS_USGS',
                fileAdapter(scene) {
                    return [ scene.sceneMetadata.links.find(l => l.rel.contains('data#')).href ];
                },
                default: true
            }, {
                name: 'MOD09A1: MODIS/Terra',
                uuid: 'a11b768b-d869-476e-a1ed-0ac3205ed761',
                uploadType: 'MODIS_USGS',
                id: 'MOD09A1',
                fileAdapter(scene) {
                    return [ scene.sceneMetadata.links.find(l => l.rel.contains('data#')).href ];
                }
            }, {
                name: 'Landsat 4 + 5 TM',
                uuid: 'e8c4d923-5a73-430d-8fe4-53bd6a12ce6a',
                uploadType: 'LANDSAT_HISTORICAL',
                id: 'Landsat4-5_TM_C1',
                fileAdapter(scene) {
                    return [scene.name];
                }
            }, {
                name: 'Landsat 7 ETM',
                uuid: '5a462d31-5744-4ab9-9e80-5dbcb118f72f',
                uploadType: 'LANDSAT_HISTORICAL',
                id: 'Landsat7_ETM_Plus_C1',
                fileAdapter(scene) {
                    return [scene.name];
                }
            }];
        }

        getSources() {
            return this.$q((resolve) => {
                resolve(this.datasources);
            });
        }

        // returns Promise()
        initRepository() {
            return this.$q((resolve) => {
                this.initDatasources();
                resolve();
            });
        }

        getFilters() {
            return [{
                param: 'datasource',
                label: 'Imagery Collection',
                type: 'search-select',
                getSources: this.getSources.bind(this)
            }, {
                params: {
                    min: 'minAcquisitionDatetime',
                    max: 'maxAcquisitionDatetime'
                },
                label: 'Date Range',
                type: 'daterange',
                default: 'The last month'
            }];
        }

        getDatasource(scene) {
            return this.$q(resolve => resolve({name: scene.sceneMetadata.dataset_id}));
        }

        getThumbnail(scene) {
            return this.$q((resolve, reject) => {
                const thumbnails = this.getThumbnails(scene);
                if (thumbnails.length) {
                    resolve(thumbnails[0].href);
                } else {
                    reject();
                }
            });
        }

        getThumbnails(scene) {
            if (scene && scene.sceneMetadata) {
                return scene.sceneMetadata.links
                    .filter(l => l.rel.toLowerCase().contains('browse'));
            }
            return [];
        }

        getPreview(scene) {
            return this.getThumbnail(scene);
        }

        getReferenceDate(scene) {
            return scene.sceneMetadata.time_start;
        }

        getScenePermissions() {
            return ['download'];
        }

        /*
          Returns a function which fetches scenes

          Function chain:
          (filters) => (bbox) => () => Future(next page of scenes)
         */
        fetchScenes(filters) {
            const baseUrl = 'https://cmr.earthdata.nasa.gov/search/granules.json';
            return (bbox) => {
                const pageSize = 25;
                let page = 1;

                return () => {
                    return this.$q((resolve, reject) => {
                        axios(baseUrl, {
                            params: {
                                // Some short_names that work:
                                // MYD09A1, MOD09A1, MOD14A2, Landsat7_ETM_Plus_C1
                                // Landsat_8_OLI_TIRS_C1 Landsat4-5_TM_C1, ISERV
                                // EO1_ALI, EO1_Hyperion
                                'short_name': filters.datasource,
                                'bounding_box': this.bboxFilterAdapter(bbox),
                                'temporal': this.dateFilterAdapter(filters),
                                'page_size': pageSize,
                                'page_num': page,
                                'sort_key': '-start_date'
                            }
                        }).then(response => {
                            page += 1;
                            resolve({
                                scenes: response.data.feed.entry.map(s =>
                                    this.incomingSceneAdapter(s, filters.datasource)
                                ),
                                hasNext: true,
                                count: 'unknown'
                            });
                        }, err => {
                            reject(err);
                        });
                    });
                };
            };
        }

        bboxFilterAdapter(bbox) {
            // Clamp bbox to lat/lon limits
            return bbox.split(',').map((p, i) => {
                if (i % 2) {
                    return Math.min(Math.max(p, -90), 90);
                }
                return Math.min(Math.max(p, -180), 180);
            }).join(',');
        }

        footprintAdapter(points) {
            if (points) {
                return [[
                    points[0][0].split(' ').reduce((coords, point, index, pointArr) => {
                        if (index % 2) {
                            coords.push([+pointArr[index], +pointArr[index - 1]]);
                            return coords;
                        }
                        return coords;
                    }, [])
                ]];
            }

            return [];
        }

        dateFilterAdapter(filters) {
            // eslint-disable-next-line
            return `${filters.minAcquisitionDatetime || ''},${filters.maxAcquisitionDatetime || ''}`;
        }

        // Transform a single scene
        incomingSceneAdapter(scene, datasource) {
            return {
                id: scene.id,
                createdAt: scene.time_start,
                createdBy: 'nasa',
                modifiedAt: scene.time_start,
                modifiedBy: 'nasa',
                owner: 'nasa',
                datasource: datasource,
                sceneMetadata: scene,
                name: scene.title,
                tileFootprint: {
                    type: 'MultiPolygon',
                    coordinates: this.footprintAdapter(scene.polygons)
                },
                dataFootprint: {
                    type: 'MultiPolygon',
                    coordinates: this.footprintAdapter(scene.polygons)
                },
                filterFields: {
                    acquisitionDate: scene.time_start
                },
                thumbnails: this.getThumbnails(scene)
            };
        }

        sceneToUploadObject(scene, projectId, user) {
            const datasource = this.datasources.find(d => d.id === scene.datasource);
            return {
                files: datasource.fileAdapter(scene),
                fileType: 'GEOTIFF',
                uploadType: datasource.uploadType,
                datasource: datasource.uuid,
                uploadStatus: 'UPLOADED',
                visibility: 'PRIVATE',
                organizationId: user.organizationId,
                projectId,
                metadata: {}
            };
        }

        /*
          Returns a function which creates an import using the given scenes
          returns: (scenes) => Future(success, failure)
        */
        addToProject(projectId, scenes) {
            return this.authService.getCurrentUser().then(user => {
                const uploads = scenes.map(s => this.sceneToUploadObject(s, projectId, user));
                return this.$q.all(
                    uploads.map(u => this.uploadService.create(u))
                );
            });
        }
    }

    app.service('CMRRepository', CMRRepository);
};

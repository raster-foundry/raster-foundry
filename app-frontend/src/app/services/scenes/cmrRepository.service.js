// import _ from 'lodash';
import axios from 'axios';

export default (app) => {
    class CMRRepository {
        constructor($q) {
            this.$q = $q;
            this.previewOnMap = false;
        }

        initDatasources() {
            this.datasources = [{
                name: 'Landsat 1-5 MSS',
                uuid: '02e8ffdb-d20d-4a50-9a12-23a9b3cf7f0d',
                id: 'Landsat_MSS'
            }, {
                name: 'Landsat 4-5 TM',
                uuid: '5ea52134-ab8e-4dd4-93bb-461bdac9dcaa',
                id: 'Landsat4-5_TM_C1'
            }, {
                name: 'Landsat 7 ETM',
                uuid: 'fa364cbb-c742-401e-8815-d69d0f042382',
                id: 'Landsat7_ETM_Plus_C1'
            }, {
                name: 'Landsat 8 OLI TIRS',
                uuid: '697a0b91-b7a8-446e-842c-97cda155554d',
                id: 'Landsat_8_OLI_TIRS_C1'
            }, {
                name: 'MODIS/Aqua',
                uuid: '73b24c83-1da9-4118-ae3f-ac601d1b701b',
                id: 'MYD09A1',
                default: true
            }, {
                name: 'MODIS/Terra',
                uuid: '73b24c83-1da9-4118-ae3f-ac601d1b701b',
                id: 'MOD09A1'
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
            }];
        }

        getDatasource(scene) {
            return this.$q(resolve => resolve({name: scene.sceneMetadata.dataset_id}));
        }

        getThumbnail(scene) {
            return this.$q(resolve => {
                const thumbnails = this.getThumbnails(scene);
                resolve(thumbnails.length ? thumbnails[0].href : false);
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
            return [];
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
                                scenes: response.data.feed.entry.map(
                                    this.incomingSceneAdapter.bind(this)
                                ),
                                hasNext: true,
                                count: 'Unknown'
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
        incomingSceneAdapter(scene) {
            return {
                id: scene.id,
                createdAt: scene.time_start,
                createdBy: 'nasa',
                modifiedAt: scene.time_start,
                modifiedBy: 'nasa',
                owner: 'nasa',
                datasource: scene.dataset_id,
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

        /*
          Returns a function which creates an import using the given scenes
          returns: (scenes) => Future(success, failure)
        */
        addToProject() {
            // @TODO: Implement
            throw Error('Not implemented');
        }
    }

    app.service('CMRRepository', CMRRepository);
};

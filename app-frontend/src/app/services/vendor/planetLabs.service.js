/* globals btoa, Uint8Array, _ */
import turfBbox from '@turf/bbox';
import turfBboxPolygon from '@turf/bbox-polygon';

export default (app) => {
    class PlanetLabsService {
        constructor(
            $log, $http
        ) {
            'ngInject';
            this.$log = $log;
            this.$http = $http;
        }

        sendHttpRequest(req) {
            return this.$http(req).then(
                (response) => {
                    return response;
                },
                (error) => {
                    return error;
                }
            );
        }

        filterScenes(apiKey, requestBody) {
            // TODO figure out how to use the browser cache for this
            let token = btoa(apiKey + ':');
            let req = {
                'method': 'POST',
                'url': 'https://api.planet.com/data/v1/quick-search',
                'headers': {
                    'Content-Type': 'application/json',
                    'Authorization': 'Basic ' + token
                },
                data: requestBody
            };

            return this.sendHttpRequest(req);
        }

        getFilteredScenesNextPage(apiKey, link) {
            let token = btoa(apiKey + ':');
            let req = {
                'method': 'GET',
                'url': link,
                'headers': {
                    'Authorization': 'Basic ' + token
                }
            };

            return this.sendHttpRequest(req);
        }


        getThumbnail(apiKey, link, size) {
            let token = btoa(apiKey + ':');
            let req = {
                'method': 'GET',
                'url': link + '?width=' + size,
                'headers': {
                    'Authorization': 'Basic ' + token,
                    'Content-Type': 'arraybuffer'
                },
                'responseType': 'arraybuffer'
            };

            return this.$http(req).then(
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

        planetFeatureToScene(planetScenes) {
            let scenes = planetScenes.features.map((feature) => {
                const tileFootprint = turfBboxPolygon(turfBbox(feature));
                return {
                    id: feature.id,
                    createdAt: feature.properties.acquired,
                    createdBy: 'planet',
                    modifiedAt: feature.properties.published,
                    modifiedBy: 'planet',
                    owner: 'planet',
                    datasource: feature.properties.item_type,
                    sceneMetadata: feature.properties,
                    name: feature.id,
                    tileFootprint: {
                        type: 'MultiPolygon',
                        coordinates: [tileFootprint.geometry.coordinates]
                    },
                    dataFootprint: {
                        type: 'MultiPolygon',
                        coordinates: feature.geometry.type === 'MultiPolygon' ?
                            feature.geometry.coordinates :
                            [feature.geometry.coordinates]
                    },
                    // eslint-disable-next-line no-underscore-dangle
                    thumbnails: [{url: feature._links.thumbnail}],
                    filterFields: {
                        cloudCover: feature.properties.cloud_cover,
                        acquisitionDate: feature.properties.acquired,
                        sunAzimuth: feature.properties.sun_azimuth,
                        sunElevation: feature.properties.sun_elevation
                    },
                    statusFields: {},
                    // eslint-disable-next-line no-underscore-dangle
                    permissions: feature._permissions
                };
            });

            return _.chunk(scenes, 15);
        }

        constructRequestBody(params, bbox) {
            let ds = params.datasource && params.datasource.length ?
                [params.datasource] :
                ['PSScene4Band', 'REOrthoTile'];
            let config = Object.keys(params).map((key) => {
                if (key === 'maxAcquisitionDatetime' && params[key]) {
                    return {
                        'type': 'DateRangeFilter',
                        'field_name': 'acquired',
                        'config': {
                            'gte': params.minAcquisitionDatetime,
                            'lte': params.maxAcquisitionDatetime
                        }
                    };
                } else if (key === 'maxCloudCover' && params[key]) {
                    return {
                        'type': 'RangeFilter',
                        'field_name': 'cloud_cover',
                        'config': {
                            'gte': params.minCloudCover || 0,
                            'lte': params.maxCloudCover || 100
                        }
                    };
                } else if (key === 'maxSunAzimuth' && params[key]) {
                    return {
                        'type': 'RangeFilter',
                        'field_name': 'sun_azimuth',
                        'config': {
                            'gte': params.minSunAzimuth || 0,
                            'lte': params.maxSunAzimuth || 360
                        }
                    };
                } else if (key === 'maxSunElevation' && params[key]) {
                    return {
                        'type': 'RangeFilter',
                        'field_name': 'sun_elevation',
                        'config': {
                            'gte': params.minSunElevation || 0,
                            'lte': params.maxSunElevation || 180
                        }
                    };
                }
                return null;
            });

            let permissionFilter = {
                'type': 'PermissionFilter',
                'config': ['assets.analytic:download']
            };

            let geometryFilter = {
                'type': 'GeometryFilter',
                'field_name': 'geometry'
            };

            if (params.shape) {
                geometryFilter.config = params.shape.geometry;
            } else {
                geometryFilter.config = {
                    'type': 'Polygon',
                    coordinates: [
                        [
                            [bbox.getNorthEast().lng, bbox.getNorthEast().lat],
                            [bbox.getSouthEast().lng, bbox.getSouthEast().lat],
                            [bbox.getSouthWest().lng, bbox.getSouthWest().lat],
                            [bbox.getNorthWest().lng, bbox.getNorthWest().lat],
                            [bbox.getNorthEast().lng, bbox.getNorthEast().lat]
                        ]
                    ]
                };
            }

            return {
                'item_types': ds,
                'filter': {
                    'type': 'AndFilter',
                    'config': _.compact(config.concat([permissionFilter, geometryFilter]))
                }
            };
        }

        uint8ToString(u8a) {
            const CHUNK_SZ = 0x8000;
            let result = [];
            for (let i = 0; i < u8a.length; i += CHUNK_SZ) {
                result.push(String.fromCharCode.apply(null, u8a.subarray(i, i + CHUNK_SZ)));
            }
            return result.join('');
        }
    }

    app.service('planetLabsService', PlanetLabsService);
};

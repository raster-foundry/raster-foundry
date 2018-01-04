/* globals btoa, Uint8Array, _ */
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

        getThumbnail(apiKey, link) {
            let token = btoa(apiKey + ':');
            let req = {
                'method': 'GET',
                'url': link,
                'headers': {
                    'Authorization': 'Basic ' + token,
                    'Content-Type': 'arraybuffer'
                },
                'responseType': 'arraybuffer'
            };

            return this.$http(req).then(
                (response) => {
                    let arr = new Uint8Array(response.data);
                    let raw = String.fromCharCode.apply(null, arr);
                    return btoa(raw);
                },
                (error) => {
                    return error;
                }
            );
        }

        planetFeatureToScene(planetScenes) {
            // TODO: may need to further reshape planet data property to match RF data properties
            let scenes = planetScenes.features.map((feature) => {
                return {
                    id: feature.id,
                    createdAt: feature.properties.acquired,
                    createdBy: 'planet',
                    modifiedAt: feature.properties.published,
                    modifiedBy: 'planet',
                    owner: 'planet',
                    datasource: feature.properties.provider,
                    sceneMetadata: feature.properties,
                    name: feature.properties.item_type,
                    tileFootprint: {
                        type: 'MultiPolygon',
                        coordinates: [feature.geometry.coordinates]
                    },
                    dataFootprint: {
                        type: 'MultiPolygon',
                        coordinates: [feature.geometry.coordinates]
                    },
                    // eslint-disable-next-line no-underscore-dangle
                    thumbnails: [{url: feature._links.thumbnail}],
                    filterFields: {
                        cloudCover: feature.properties.cloud_cover,
                        acquisitionDate: feature.properties.acquired,
                        sunAzimuth: feature.properties.sun_azimuth,
                        sunElevation: feature.properties.sun_elevation
                    },
                    statusFields: {}
                };
            });

            return _.chunk(scenes, 15);
        }

        constructRequestBody(params, bbox) {
            let ds = params.datasource && params.datasource.length ? params.datasource :
                ['PSScene3Band', 'PSScene4Band', 'PSOrthoTile', 'REOrthoTile'];
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

            let permission = [{
                'type': 'PermissionFilter',
                'config': ['assets.analytic:download']
            }];

            let bboxFilter = [{
                'type': 'GeometryFilter',
                'field_name': 'geometry',
                'config': {
                    'type': 'Polygon',
                    'coordinates': [
                        [
                            [bbox.getNorthEast().lng, bbox.getNorthEast().lat],
                            [bbox.getSouthEast().lng, bbox.getSouthEast().lat],
                            [bbox.getSouthWest().lng, bbox.getSouthWest().lat],
                            [bbox.getNorthWest().lng, bbox.getNorthWest().lat],
                            [bbox.getNorthEast().lng, bbox.getNorthEast().lat]
                        ]
                    ]
                }
            }];

            return {
                'item_types': ds,
                'filter': {
                    'type': 'AndFilter',
                    'config': _.compact(config.concat(permission).concat(bboxFilter))
                }
            };
        }
    }

    app.service('planetLabsService', PlanetLabsService);
};

/* global $ */
/* eslint-disable camelcase */
const appId = 'mXP4DZFBZGyBmuZBKNeo';
const appCode = 'kBWb6Z7ZLcuQanT_RoP60A';

export default (app) => {
    class GeocodeService {
        constructor() { }

        getLocationSuggestions(query) {
            const baseUrl = 'https://autocomplete.geocoder.cit.api.here.com/6.2/suggest.json';
            const requestParams = {
                app_id: appId,
                app_code: appCode,
                query: query
            };
            return $.get(baseUrl, requestParams);
        }

        getLocation(locationId) {
            const baseUrl = 'https://geocoder.cit.api.here.com/6.2/geocode.json';
            const requestParams = {
                locationid: locationId,
                gen: 9,
                jsonattributes: 1,
                app_id: appId,
                app_code: appCode
            };
            return $.get(baseUrl, requestParams);
        }
    }

    app.service('geocodeService', GeocodeService);
};

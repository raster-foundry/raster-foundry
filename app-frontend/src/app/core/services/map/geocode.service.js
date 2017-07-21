/* global $, BUILDCONFIG */
/* eslint-disable camelcase */

export default (app) => {
    class GeocodeService {
        constructor() { }

        getLocationSuggestions(query) {
            const baseUrl = 'https://autocomplete.geocoder.cit.api.here.com/6.2/suggest.json';
            const requestParams = {
                app_id: BUILDCONFIG.HERE_APP_ID,
                app_code: BUILDCONFIG.HERE_APP_CODE,
                query: query
            };
            return $.get(baseUrl, requestParams);
        }

        getLocation(locationId) {
            const baseUrl = 'https://geocoder.cit.api.here.com/6.2/geocode.json';
            const requestParams = {
                app_id: BUILDCONFIG.HERE_APP_ID,
                app_code: BUILDCONFIG.HERE_APP_CODE,
                locationid: locationId,
                gen: 9,
                jsonattributes: 1
            };
            return $.get(baseUrl, requestParams);
        }
    }

    app.service('geocodeService', GeocodeService);
};

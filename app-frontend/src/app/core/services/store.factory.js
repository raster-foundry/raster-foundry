'use strict';

export default function (app) {
    function storeFactory() {
        return {
            countries: ['USA', 'UK', 'Ukraine']
        };
    }
    app.factory('store', storeFactory);
}

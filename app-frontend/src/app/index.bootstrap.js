/* globals document BUILDCONFIG */
import angular from 'angular';

// main App module
import './index.module';

if (!BUILDCONFIG.THEME || BUILDCONFIG.THEME === 'default') {
    require('../assets/styles/sass/app.scss');
} else if (BUILDCONFIG.THEME) {
    require(`../assets/styles/sass/theme/${BUILDCONFIG.THEME}/app.scss`);
}

import deferredBootstrapper from 'angular-deferred-bootstrap';

angular.element(document).ready(function () {
    deferredBootstrapper.bootstrap({
        element: document,
        module: 'rasterFoundry',
        bootstrapConfig: {
            strictDi: true
        },
        resolve: {
            APP_CONFIG: ['$http', ($http) => {
                let url = `${BUILDCONFIG.API_HOST}/config`;
                return $http.get(url).then(
                    (result) => result,
                    (error) => ({error: error})
                );
            }]
        }
    });
});

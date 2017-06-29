/* globals document BUILDCONFIG */

// index.html page to dist folder
import '!!file-loader?name=[name].[ext]!../favicon.ico';

// main App module
import './index.module';

import '../assets/styles/sass/app.scss';

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

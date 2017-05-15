/* globals process */

import assetLogo from '../assets/images/logo-raster-foundry.png';


function config( // eslint-disable-line max-params
    $logProvider, $compileProvider,
    jwtInterceptorProvider,
    $httpProvider, configProvider, APP_CONFIG,
    featureFlagsProvider, RollbarProvider
) {
    'ngInject';

    // Enable log
    $logProvider.debugEnabled(true);
    $compileProvider.debugInfoEnabled(false);

    jwtInterceptorProvider.tokenGetter = function (authService) {
        'ngInject';
        return authService.token();
    };

    $httpProvider.interceptors.push('jwtInterceptor');
    $httpProvider.interceptors.push(function ($q, $injector) {
        'ngInject';
        return {
            responseError: function (rejection) {
                let authService = $injector.get('authService');
                if (rejection.status === 401 &&
                    rejection.config.url.indexOf('/api') === 0) {
                    authService.logout();
                }
                return $q.reject(rejection);
            }
        };
    });

    RollbarProvider.init({
        accessToken: APP_CONFIG.rollbarClientToken,
        captureUncaught: true,
        payload: {
            environment: APP_CONFIG.clientEnvironment
        }
    });

    configProvider.init(process.env);

    featureFlagsProvider.setInitialFlags(APP_CONFIG.featureFlags);
}

export default config;

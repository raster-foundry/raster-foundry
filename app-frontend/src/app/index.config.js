/* globals process */

'use strict';

function config( // eslint-disable-line max-params
    $logProvider, $compileProvider, authProvider, jwtInterceptorProvider,
    $httpProvider, configProvider, APP_CONFIG
) {
    'ngInject';

    // Enable log
    $logProvider.debugEnabled(true);
    $compileProvider.debugInfoEnabled(false);

    if (!APP_CONFIG.error) {
        authProvider.init({
            domain: 'raster-foundry.auth0.com',
            clientID: APP_CONFIG.clientId,
            loginState: 'login',
            sso: false
        }, require('auth0-lock'));

        authProvider.on('logout', function (store, $state) {
            'ngInject';
            store.remove('profile');
            store.remove('token');
            store.remove('accessToken');
            $state.go('browse');
        });

        jwtInterceptorProvider.tokenGetter = function (auth) {
            'ngInject';
            return auth.idToken;
        };
    }

    $httpProvider.interceptors.push('jwtInterceptor');

    configProvider.init(process.env);
}

export default config;

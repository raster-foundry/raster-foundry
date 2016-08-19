'use strict';

function config(
    $logProvider, $compileProvider, authProvider, jwtInterceptorProvider, $httpProvider
) {
    'ngInject';

    // Enable log
    $logProvider.debugEnabled(true);
    $compileProvider.debugInfoEnabled(false);

    authProvider.init({
        domain: 'raster-foundry.auth0.com',
        clientID: process.env.CLIENT_ID,
        loginState: 'login',
        sso: false
    }, require('auth0-lock'));

    authProvider.on('logout', function (store, $state) {
        'ngInject';
        store.remove('profile');
        store.remove('token');
        store.remove('accessToken');
        $state.go('login');
    });

    jwtInterceptorProvider.tokenGetter = function (auth) {
        'ngInject';
        return auth.idToken;
    };
    $httpProvider.interceptors.push('jwtInterceptor');
}

export default config;

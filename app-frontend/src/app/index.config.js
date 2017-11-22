/* globals process BUILDCONFIG */
import reducers from './redux/reducers';
import { combineReducers } from 'redux';
import { asyncDispatchMiddleware } from '_redux/middleware/asyncDispatch';
import promiseMiddleware from 'redux-promise-middleware';
import thunk from 'redux-thunk';

if (BUILDCONFIG.LOGOFILE) {
    require(`../assets/images/${BUILDCONFIG.LOGOFILE}`);
} else {
    require('../assets/images/logo-raster-foundry.png');
}

function config( // eslint-disable-line max-params
    $logProvider, $compileProvider,
    jwtInterceptorProvider, $ngReduxProvider,
    $httpProvider, configProvider, APP_CONFIG,
    featureFlagsProvider, RollbarProvider
) {
    'ngInject';
    // redux
    $ngReduxProvider.createStoreWith(
        combineReducers(reducers),
        [thunk, promiseMiddleware(), asyncDispatchMiddleware],
        // redux devtools chrome extension:
        // https://chrome.google.com/webstore/detail/redux-devtools/lmhkpmbekcpmknklioeibfkpmmfibljd
        // eslint-disable-next-line
        window.__REDUX_DEVTOOLS_EXTENSION__ ? [window.__REDUX_DEVTOOLS_EXTENSION__()] : []
    );


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

/* globals process */

import assetLogo from '../assets/images/logo-raster-foundry.png';


function config( // eslint-disable-line max-params
    $logProvider, $compileProvider,
    jwtInterceptorProvider,
    $httpProvider, configProvider, APP_CONFIG,
    lockProvider, featureFlagsProvider, RollbarProvider
) {
    'ngInject';

    // Enable log
    $logProvider.debugEnabled(true);
    $compileProvider.debugInfoEnabled(false);

    if (!APP_CONFIG.error) {
        lockProvider.init({
            clientID: APP_CONFIG.clientId,
            domain: APP_CONFIG.auth0Domain,
            options: {
                closable: false,
                auth: {
                    redirect: false,
                    sso: true
                },
                theme: {
                    logo: assetLogo,
                    primaryColor: '#5e509b'
                },
                additionalSignUpFields: [{
                    name: 'companyName',
                    placeholder: 'Company name'
                }, {
                    name: 'companySize',
                    placeholder: 'How large is your company?'
                }, {
                    name: 'reference',
                    placeholder: 'How\'d you find out about us?'
                }, {
                    name: 'phoneNumber',
                    placeholder: 'Phone Number'
                }]
            }
        });

        jwtInterceptorProvider.tokenGetter = function (authService) {
            'ngInject';
            return authService.token();
        };
    }

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
            environment: process.env.NODE_ENV || 'production'
        }
    });

    configProvider.init(process.env);

    featureFlagsProvider.setInitialFlags(APP_CONFIG.featureFlags);
}

export default config;

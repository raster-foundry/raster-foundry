function runBlock(
    $rootScope, jwtHelper, $state, $location, APP_CONFIG,
    authService, localStorage, rollbarWrapperService, intercomService,
    featureFlags, perUserFeatureFlags
) {
    'ngInject';
    let flagsPromise;

    if (authService.verifyAuthCache()) {
        flagsPromise = perUserFeatureFlags.load();
        featureFlags.set(flagsPromise);
    }

    $rootScope.$on('$stateChangeStart', function (e, toState, params) {
        function setupState() {
            if (APP_CONFIG.error && toState.name !== 'error') {
                e.preventDefault();
                $state.go('error');
            } else if (toState.name !== 'login' && !authService.verifyAuthCache()) {
                e.preventDefault();
                rollbarWrapperService.init();
                intercomService.shutdown();
                $state.go('login');
            } else if (toState.name !== 'login' && toState.name !== 'callback') {
                rollbarWrapperService.init(authService.profile());
                intercomService.bootWithUser(authService.profile());
                if (toState.redirectTo) {
                    e.preventDefault();
                    $state.go(toState.redirectTo, params);
                }
            }
        }
        // TODO: I'm not sure exactly where this lies on the continuum between awful and the worst
        // thing ever, but it's pretty bad. We should either refactor our app initialization so this
        // is easier, or refactor FeatureFlags to deal in promises.
        // Note that on initial login, the feature flags get populated in the AuthService.
        if (flagsPromise) {
            flagsPromise.then(setupState);
        } else {
            setupState();
        }
    });

    $rootScope.$on('$locationChangeStart', function () {
        function setupState() {
            let token = localStorage.get('id_token');
            if (token) {
                if (!authService.verifyAuthCache()) {
                    rollbarWrapperService.init();
                    authService.login(token);
                }
            } else {
                intercomService.shutdown();
                rollbarWrapperService.init();
                $state.go('login');
            }
        }
        if (flagsPromise) {
            flagsPromise.then(setupState);
        } else {
            setupState();
        }
    });
}

export default runBlock;

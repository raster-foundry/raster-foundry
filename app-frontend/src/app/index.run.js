/* global BUILDCONFIG */
function runBlock(
    $rootScope, jwtHelper, $state, $location, $window, APP_CONFIG,
    $ngRedux, $timeout,
    authService, localStorage, rollbarWrapperService, intercomService,
    featureFlags, perUserFeatureFlags, modalService
) {
    'ngInject';
    let flagsPromise;

    $ngRedux.subscribe(() => {
        $timeout(() => {
            $rootScope.$apply(() => {});
        }, 100);
    });

    if (authService.verifyAuthCache()) {
        flagsPromise = perUserFeatureFlags.load();
        featureFlags.set(flagsPromise);
    }
    if (APP_CONFIG.error) {
        $state.go('error');
    }

    $rootScope.$on('$stateChangeStart', function (e, toState, params) {
        function setupState(route) {
            let appName = BUILDCONFIG.APP_NAME;
            $window.document.title = toState.title ?
                `${appName} - ${toState.title}` : appName;
            if (APP_CONFIG.error && toState.name !== 'error') {
                e.preventDefault();
                if (!localStorage.get('authUrlRestore')) {
                    localStorage.set('authUrlRestore', route);
                }
                $state.go('error');
            } else if (toState.bypassAuth && !authService.verifyAuthCache()) {
                rollbarWrapperService.init();
            } else if (!toState.bypassAuth && !authService.verifyAuthCache()) {
                e.preventDefault();
                rollbarWrapperService.init();
                intercomService.shutdown();
                if (!localStorage.get('authUrlRestore')) {
                    localStorage.set('authUrlRestore', route);
                }
                $state.go('login');
            } else if (!toState.bypassAuth && toState.name !== 'callback') {
                rollbarWrapperService.init(authService.getProfile());
                intercomService.bootWithUser(authService.getProfile());
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
            flagsPromise.then(
                () => setupState({path: $location.path(), search: $location.search()})
            );
        } else {
            setupState({path: $location.path(), search: $location.search()});
        }
    });

    $rootScope.$on('$stateChangeSuccess', () => {
        modalService.closeActiveModal();
    });

    $rootScope.$on('$locationChangeStart', function () {
        function setupState(route) {
            let idToken = localStorage.get('idToken');
            let accessToken = localStorage.get('accessToken');

            if (idToken && accessToken) {
                if (!authService.verifyAuthCache()) {
                    rollbarWrapperService.init();
                    if (!localStorage.get('authUrlRestore')) {
                        localStorage.set('authUrlRestore', route);
                    }
                    authService.login(accessToken, idToken);
                }
            } else if (!route.path.includes('login')) {
                intercomService.shutdown();
                rollbarWrapperService.init();
                if (!localStorage.get('authUrlRestore')) {
                    localStorage.set('authUrlRestore', route);
                }
                $state.go('login');
            }
        }
        if (flagsPromise) {
            flagsPromise.then(
                () => setupState({path: $location.path(), search: $location.search()})
            );
        } else {
            setupState({path: $location.path(), search: $location.search()});
        }
    });
    $rootScope.autoInject = function (context, args) {
        context.constructor.$inject.forEach((injectable, idx) => {
            context[injectable] = args[idx];
        });
    };
}

export default runBlock;

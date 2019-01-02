/* global BUILDCONFIG */
import $ from 'jquery';
function runBlock(
    $rootScope, jwtHelper, $state, $location, $window, APP_CONFIG,
    $ngRedux, $timeout, $transitions,
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


    $transitions.onStart({}, (transition) => {
        const toState = transition.to();
        const fromState = transition.from();
        function setupState(route) {
            let appName = BUILDCONFIG.APP_NAME;
            $window.document.title = toState.title ?
                `${appName} - ${toState.title}` : appName;

            let idToken = localStorage.get('idToken');
            let accessToken = localStorage.get('accessToken');

            if (APP_CONFIG.error && toState.name !== 'error') {
                if (!localStorage.get('authUrlRestore')) {
                    localStorage.set('authUrlRestore', route);
                }
                return transition.router.stateService.target('error');
            } else if (APP_CONFIG.error && toState.name !== 'error') {
                if (!localStorage.get('authUrlRestore')) {
                    localStorage.set('authUrlRestore', route);
                }
                return transition.router.stateService.target('error');
            } else if (toState.bypassAuth && !authService.verifyAuthCache()) {
                rollbarWrapperService.init();
            } else if (!toState.bypassAuth && !authService.verifyAuthCache()) {
                rollbarWrapperService.init();
                intercomService.shutdown();
                if (!localStorage.get('authUrlRestore')) {
                    localStorage.set('authUrlRestore', route);
                }
                return transition.router.stateService.target('login');
            } else if (!toState.bypassAuth && toState.name !== 'callback') {
                rollbarWrapperService.init(authService.getProfile());
                intercomService.bootWithUser(authService.getProfile());
                if (toState.redirectTo) {
                    return transition.router.stateService.target(
                        toState.redirectTo, Object.assign({}, transition.params())
                    );
                }
            } else if (idToken && accessToken) {
                if (!authService.verifyAuthCache()) {
                    rollbarWrapperService.init();
                    if (!localStorage.get('authUrlRestore')) {
                        localStorage.set('authUrlRestore', route);
                    }
                    if (!authService.login(accessToken, idToken)) {
                        return transition.router.stateService.target('login');
                    }
                }
            } else if (!route.path.includes('login')) {
                intercomService.shutdown();
                rollbarWrapperService.init();
                if (!localStorage.get('authUrlRestore')) {
                    localStorage.set('authUrlRestore', route);
                }
                return transition.router.stateService.target('login');
            }
            return true;
        }

        if (
            toState.name !== fromState.name &&
                toState.redirectTo !== fromState.name &&
                toState.resolve
        ) {
            $rootScope.isLoadingResolves = true;
        }
        // TODO: I'm not sure exactly where this lies on the continuum between awful and the worst
        // thing ever, but it's pretty bad. We should either refactor our app initialization so this
        // is easier, or refactor FeatureFlags to deal in promises.
        // Note that on initial login, the feature flags get populated in the AuthService.
        if (flagsPromise) {
            return flagsPromise.then(
                () => setupState({path: $location.path(), search: $location.search()})
            ).catch(e => {
                if (toState.name === 'error') {
                    return true;
                }
                return transition.router.stateService.target('error');
            });
        }
        return setupState({path: $location.path(), search: $location.search()});
    });

    $transitions.onSuccess({}, (transition) => {
        $rootScope.isLoadingResolves = false;
        modalService.closeActiveModal();
    });

    $rootScope.autoInject = function (context, args) {
        context.constructor.$inject.forEach((injectable, idx) => {
            switch (injectable) {
            case '$element':
            case '$document':
                context[injectable] = $(args[idx]);
                break;
            default:
                context[injectable] = args[idx];
            }
        });
    };
}

export default runBlock;

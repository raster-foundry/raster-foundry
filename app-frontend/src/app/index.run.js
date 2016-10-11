function runBlock( // eslint-disable-line max-params
    $rootScope, auth, store, jwtHelper, $state, $location, APP_CONFIG
) {
    'ngInject';
    $rootScope.$on('$stateChangeStart', function (e, toState) {
        if (APP_CONFIG.error && toState.name !== 'error') {
            e.preventDefault();
            $state.go('error');
        }
    });
    $rootScope.$on('$locationChangeStart', function () {
        let token = store.get('token');
        if (token) {
            if (!jwtHelper.isTokenExpired(token)) {
                if (!auth.isAuthenticated) {
                    auth.authenticate(store.get('profile'), token);
                }
            }
        }
    });
    auth.hookEvents();
}

export default runBlock;

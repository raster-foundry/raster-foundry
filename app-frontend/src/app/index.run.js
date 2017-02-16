function runBlock( // eslint-disable-line max-params
    $rootScope, store, jwtHelper, $state, $location, APP_CONFIG, authService
) {
    'ngInject';

    $rootScope.$on('$stateChangeStart', function (e, toState) {
        if (APP_CONFIG.error && toState.name !== 'error') {
            e.preventDefault();
            $state.go('error');
        }
    });

    $rootScope.$on('$locationChangeStart', function () {
        let token = store.get('id_token');
        if (token) {
            if (!authService.isLoggedIn) {
                authService.login(token);
            }
        } else {
            authService.login();
        }
    });
}

export default runBlock;

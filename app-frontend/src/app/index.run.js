function runBlock( // eslint-disable-line max-params
    $rootScope, jwtHelper, $state, $location, APP_CONFIG, authService, localStorage
) {
    'ngInject';

    $rootScope.$on('$stateChangeStart', function (e, toState) {
        if (APP_CONFIG.error && toState.name !== 'error') {
            e.preventDefault();
            $state.go('error');
        } else if (toState.name !== 'login' && !authService.verifyAuthCache()) {
            e.preventDefault();
            $state.go('login');
        }
    });

    $rootScope.$on('$locationChangeStart', function () {
        let token = localStorage.get('id_token');
        if (token) {
            if (!authService.verifyAuthCache()) {
                authService.login(token);
            }
        } else {
            $state.go('login');
        }
    });
}

export default runBlock;

function runBlock( // eslint-disable-line max-params
    $rootScope, jwtHelper, $state, $location, APP_CONFIG,
    authService, localStorage, rollbarWrapperService
) {
    'ngInject';

    $rootScope.$on('$stateChangeStart', function (e, toState) {
        if (APP_CONFIG.error && toState.name !== 'error') {
            e.preventDefault();
            $state.go('error');
        } else if (toState.name !== 'login' && !authService.verifyAuthCache()) {
            e.preventDefault();
            rollbarWrapperService.init();
            $state.go('login');
        } else {
            rollbarWrapperService.init(authService.profile());
        }
    });

    $rootScope.$on('$locationChangeStart', function () {
        let token = localStorage.get('id_token');
        if (token) {
            if (!authService.verifyAuthCache()) {
                rollbarWrapperService.init();
                authService.login(token);
            }
        } else {
            rollbarWrapperService.init();
            $state.go('login');
        }
    });
}

export default runBlock;

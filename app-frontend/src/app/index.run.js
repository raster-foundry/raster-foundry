function runBlock(
    $rootScope, jwtHelper, $state, $location, APP_CONFIG,
    authService, localStorage, rollbarWrapperService, intercomService
) {
    'ngInject';

    $rootScope.$on('$stateChangeStart', function (e, toState) {
        if (APP_CONFIG.error && toState.name !== 'error') {
            e.preventDefault();
            $state.go('error');
        } else if (toState.name !== 'login' && !authService.verifyAuthCache()) {
            e.preventDefault();
            rollbarWrapperService.init();
            intercomService.shutdown();
            $state.go('login');
        } else if (toState.name !== 'login') {
            rollbarWrapperService.init(authService.profile());
            intercomService.bootWithUser(authService.profile());
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
            intercomService.shutdown();
            rollbarWrapperService.init();
            $state.go('login');
        }
    });
}

export default runBlock;

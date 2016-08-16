function runBlock($rootScope, auth, store, jwtHelper) {
    'ngInject';

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

export default function (app) {
    function loginPagePrealoading($q, $ocLazyLoad) {
        'ngInject';

        let deferred = $q.defer();
        require.ensure([], function (require) {
            let loginModule = require('../../pages/login/login.module');
            $ocLazyLoad.load({
                name: loginModule.name
            });
            deferred.resolve(loginModule.controller);
        });
        return deferred.promise;
    }
    function resolverProvider() {
        this.loginPagePrealoading = loginPagePrealoading;
        this.$get = function () {
            return this;
        };
    }


    app.provider('resolver', resolverProvider);
}

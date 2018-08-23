import angular from 'angular';

class LoginController {
    constructor($state, $location, authService, localStorage) {
        'ngInject';
        this.$state = $state;
        this.$location = $location;
        this.authService = authService;
        this.localStorage = localStorage;
    }

    $onInit() {
        const hash = this.$location.hash();
        if (hash) {
            this.authService.onRedirectComplete(hash);
        } else if (this.authService.verifyAuthCache()) {
            const restore = this.localStorage.get('authUrlRestore');
            if (restore) {
                this.$location.path(restore.path).search(restore.search);
                this.localStorage.remove('authUrlRestore');
            } else {
                this.$state.go('home');
            }
        } else {
            this.authService.clearAuthStorage();
            this.authService.login();
        }
    }
}

const LoginModule = angular.module('pages.login', []);
LoginModule.controller('LoginController', LoginController);
export default LoginModule;

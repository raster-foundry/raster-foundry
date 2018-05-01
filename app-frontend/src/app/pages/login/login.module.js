import angular from 'angular';

class LoginController {
    constructor($state, $location, authService) {
        'ngInject';
        this.$state = $state;
        this.$location = $location;
        this.authService = authService;
    }

    $onInit() {
        const hash = this.$location.hash();
        if (hash) {
            this.authService.onRedirectComplete(hash);
        }
        if (this.authService.verifyAuthCache()) {
            this.$state.go('home');
        } else {
            this.authService.clearAuthStorage();
            this.authService.login();
        }
    }
}

const LoginModule = angular.module('pages.login', []);
LoginModule.controller('LoginController', LoginController);
export default LoginModule;

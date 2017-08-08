export default class LoginController {
    constructor($state, $location, authService) {
        'ngInject';
        this.$state = $state;
        this.$location = $location;
        this.authService = authService;
    }

    $onInit() {
        const hash = this.$location.hash();
        if (hash) {
            this.authService.onImpersonation(hash);
        }
        if (this.authService.verifyAuthCache()) {
            this.$state.go('home');
        } else {
            this.authService.login();
        }
    }
}

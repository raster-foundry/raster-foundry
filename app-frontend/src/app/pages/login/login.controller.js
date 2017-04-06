export default class LoginController {
    constructor(authService, $state) {
        'ngInject';
        if (authService.verifyAuthCache()) {
            $state.go('home');
        } else {
            authService.login();
        }
    }
}

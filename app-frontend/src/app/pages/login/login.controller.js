export default class LoginController {
    constructor(authService, $state) {
        'ngInject';
        if (authService.isLoggedIn) {
            $state.go('browse');
        } else {
            authService.login();
        }
    }
}

/* globals process */
class AccountController {
    constructor($log, localStorage, authService) {
        'ngInject';
        this.$log = $log;
        this.env = process.env.NODE_ENV;
        this.authService = authService;

        this.profile = localStorage.get('profile');
        this.provider = this.profile.identities[0].provider;
    }
}

export default AccountController;

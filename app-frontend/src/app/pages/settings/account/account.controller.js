/* globals process */
class AccountController {
    constructor($log) {
        'ngInject';
        this.$log = $log;
        this.env = process.env.NODE_ENV;
    }
}

export default AccountController;

class AccountController {
    constructor($log, auth) {
        'ngInject';
        const self = this;
        self.$log = $log;
        self.auth = auth;

        $log.debug('AccountController initialized');
    }
}

export default AccountController;

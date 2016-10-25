class AccountController {
    constructor($log) {
        'ngInject';
        const self = this;
        self.$log = $log;

        $log.debug('AccountController initialized');
    }
}

export default AccountController;

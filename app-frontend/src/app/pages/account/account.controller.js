import assetLogo from '../../../assets/images/logo-raster-foundry.png';

class AccountController {
    constructor($log, auth) {
        'ngInject';
        let self = this;

        self.auth = auth;

        $log.debug('AccountController initialized');
        self.assetLogo = assetLogo;
        self.profile = Object.assign({}, auth.profile);
    }
}

export default AccountController;

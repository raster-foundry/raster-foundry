import assetLogo from '../../../assets/images/logo-raster-foundry.png';

class AccountController {
    constructor($log) {
        'ngInject';
        $log.debug('AccountController initialized');
        this.assetLogo = assetLogo;
    }
}

export default AccountController;

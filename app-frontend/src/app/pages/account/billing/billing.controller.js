import assetLogo from '../../../../assets/images/logo-raster-foundry.png';

class BillingController {
    constructor($log) {
        'ngInject';
        $log.debug('BillingController initialized');
        this.assetLogo = assetLogo;
    }
}

export default BillingController;

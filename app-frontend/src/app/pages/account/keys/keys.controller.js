import assetLogo from '../../../../assets/images/logo-raster-foundry.png';

class KeysController {
    constructor($log) {
        'ngInject';
        $log.debug('KeysController initialized');
        this.assetLogo = assetLogo;
    }
}

export default KeysController;

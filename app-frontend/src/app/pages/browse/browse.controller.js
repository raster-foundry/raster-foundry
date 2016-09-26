import assetLogo from '../../../assets/images/logo-raster-foundry.png';

class BrowseController {
    constructor($log, auth) {
        'ngInject';
        const self = this;
        self.$log = $log;
        self.auth = auth;

        $log.debug('BrowseController initialized');
        self.assetLogo = assetLogo;
    }
}

export default BrowseController;

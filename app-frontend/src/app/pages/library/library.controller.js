import assetLogo from '../../../assets/images/logo-raster-foundry.png';

class LibraryController {
    constructor($log) {
        'ngInject';
        $log.debug('LibraryController initialized');
        this.assetLogo = assetLogo;
    }
}

export default LibraryController;

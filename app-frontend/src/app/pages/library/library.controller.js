import assetLogo from '../../../assets/images/logo-raster-foundry.png';

class LibraryController {
    constructor($log, auth) {
        'ngInject';
        const self = this;
        self.$log = $log;
        self.auth = auth;

        $log.debug('LibraryController initialized');
        self.assetLogo = assetLogo;
    }

    logout() {
        const self = this;

        self.$log.log('signing out');
        self.auth.signout();
    }
}

export default LibraryController;

import assetLogo from '../../../assets/images/logo-raster-foundry.png';

// rfNavBar controller class
export default class NavBarController {
    constructor( // eslint-disable-line max-params
        $log, $state, store, $scope, APP_CONFIG, authService
    ) {
        'ngInject';

        this.$log = $log;
        this.$state = $state;
        this.store = store;
        this.authService = authService;

        if (APP_CONFIG.error) {
            this.loadError = true;
        }

        this.optionsOpen = false;
        this.assetLogo = assetLogo;

        $log.debug('Navbar controller initialized');
    }

    signin() {
        this.authService.login();
    }

    logout() {
        this.authService.logout();
    }
}

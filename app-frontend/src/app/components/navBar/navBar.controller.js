import assetLogo from '../../../assets/images/logo-raster-foundry.png';

export default class NavBarController {
    constructor( // eslint-disable-line max-params
        $log, $state, $scope, APP_CONFIG, authService, localStorage
    ) {
        'ngInject';

        this.$log = $log;
        this.$state = $state;
        this.localStorage = localStorage;
        this.authService = authService;

        if (APP_CONFIG.error) {
            this.loadError = true;
        }

        this.optionsOpen = false;
        this.assetLogo = assetLogo;
    }

    signin() {
        this.authService.login();
    }

    logout() {
        this.authService.logout();
    }
}

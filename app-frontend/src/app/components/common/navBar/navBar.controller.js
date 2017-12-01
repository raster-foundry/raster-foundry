/* global BUILDCONFIG */

let assetLogo = BUILDCONFIG.LOGOFILE ?
    require(`../../../../assets/images/${BUILDCONFIG.LOGOFILE}`) :
    require('../../../../assets/images/raster-foundry-logo.svg');

assetLogo = BUILDCONFIG.LOGOURL || assetLogo;

export default class NavBarController {
    constructor( // eslint-disable-line max-params
        $state, APP_CONFIG, authService
    ) {
        'ngInject';
        if (APP_CONFIG.error) {
            this.loadError = true;
        }

        this.$state = $state;
        this.authService = authService;
    }

    $onInit() {
        this.optionsOpen = false;
        this.assetLogo = assetLogo;
    }

    hideLabels() {
        return this.$state.current.name.startsWith('projects.edit');
    }

    signin() {
        this.authService.login();
    }

    logout() {
        this.authService.logout();
    }
}

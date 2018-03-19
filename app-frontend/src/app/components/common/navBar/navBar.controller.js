/* global BUILDCONFIG, HELPCONFIG, _ */

let assetLogo = BUILDCONFIG.LOGOFILE ?
    require(`../../../../assets/images/${BUILDCONFIG.LOGOFILE}`) :
    require('../../../../assets/images/raster-foundry-logo.svg');

assetLogo = BUILDCONFIG.LOGOURL || assetLogo;

export default class NavBarController {
    constructor( // eslint-disable-line max-params
        $rootScope, $log, $state, APP_CONFIG, authService
    ) {
        'ngInject';
        if (APP_CONFIG.error) {
            this.loadError = true;
        }
        this.$rootScope = $rootScope;
        this.$log = $log;
        this.$state = $state;
        this.authService = authService;
    }

    $onInit() {
        this.optionsOpen = false;
        this.assetLogo = assetLogo;

        this.HELPCONFIG = HELPCONFIG;
        this.helpDocs = this.HELPCONFIG ? this.HELPCONFIG.HELP_DOCS : {};
        if (!_.isEmpty(this.helpDocs)) {
            this.showHelpCenter = true;
            this.setRootDocs();
            this.setPageDocs(this.$state);
            this.$rootScope.$on('$stateChangeSuccess', () => {
                this.setPageDocs(this.$state);
            });
        }
    }

    setRootDocs() {
        this.rootDocs = this.helpDocs[''];
    }

    setPageDocs(state) {
        if (state.current && state.current.name && state.current.name.length) {
            this.pageDocs = this.helpDocs[state.current.name.replace('.', '_')];
        }
    }

    onHelpToggled() {
        this.isHelpOpened = !this.isHelpOpened;
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

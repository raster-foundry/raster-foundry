/* global BUILDCONFIG, HELPCONFIG, _ */

let assetLogo = BUILDCONFIG.LOGOFILE ?
    require(`../../../../assets/images/${BUILDCONFIG.LOGOFILE}`) :
    require('../../../../assets/images/raster-foundry-logo.svg');

assetLogo = BUILDCONFIG.LOGOURL || assetLogo;

const Plyr = require('plyr/dist/plyr.js');

const plyrStyle = {
    'default': {
        'position': 'fixed',
        'right': 0,
        'top': '6rem',
        'width': '600px',
        'z-index': 999
    },
    'fullscreen': {
        'position': 'relative',
        'top': '0px',
        'width': '100%'
    }
};

export default class NavBarController {
    constructor( // eslint-disable-line max-params
        $rootScope, $scope, $log, $state, $document, $sce, $timeout, APP_CONFIG, authService
    ) {
        'ngInject';
        if (APP_CONFIG.error) {
            this.loadError = true;
        }
        this.$rootScope = $rootScope;
        this.$scope = $scope;
        this.$log = $log;
        this.$state = $state;
        this.$document = $document;
        this.$sce = $sce;
        this.$timeout = $timeout;
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

    onHelpVideoClicked(doc) {
        this.videoUlr = this.$sce.trustAsResourceUrl(doc.link + '?rel=0&controls=0&showinfo=0');
        this.$timeout(() => {
            this.initPlyr();
        }, 100);
    }

    initPlyr() {
        this.plyr = new Plyr('#player');
        this.plyr.on('enterfullscreen', this.onEnterFullscreen.bind(this));
        this.plyr.on('exitfullscreen', this.onExitFullscreen.bind(this));
        this.showCustomBnt = true;
        this.showVideo = true;
    }

    onEnterFullscreen() {
        const plyrContainer = angular.element(this.$document[0].querySelector('#player'));
        plyrContainer.css(plyrStyle.fullscreen);
    }

    onExitFullscreen() {
        const plyrContainer = angular.element(this.$document[0].querySelector('#player'));
        plyrContainer.css(plyrStyle.default);
    }

    onShowCustomBnts() {
        this.showCustomBnt = true;
        this.$scope.$evalAsync();
    }

    onHideCustomBnts() {
        this.showCustomBnt = false;
        this.$scope.$evalAsync();
    }

    onVideoClose(e) {
        e.stopPropagation();
        delete this.videoUlr;
        this.showCustomBnt = false;
        this.showVideo = false;
        this.showMini = false;
    }

    onVideoCollapse() {
        this.showVideo = false;
        this.showMini = true;
        this.showCustomBnt = false;
    }

    onMiniVideoHover(status) {
        this.isMiniVideoHover = status;
    }

    onMiniVideoClick() {
        if (this.videoUlr) {
            this.showVideo = true;
            this.showCustomBnt = true;
            this.showMini = false;
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

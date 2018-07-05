/* global BUILDCONFIG, HELPCONFIG, _ */

let assetLogo = BUILDCONFIG.LOGOFILE ?
    require(`../../../../assets/images/${BUILDCONFIG.LOGOFILE}`) :
    require('../../../../assets/images/raster-foundry-logo.svg');

assetLogo = BUILDCONFIG.LOGOURL || assetLogo;

const Plyr = require('plyr/dist/plyr.js');

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

        this.helpDocs = HELPCONFIG && HELPCONFIG.HELP_DOCS ? HELPCONFIG.HELP_DOCS : {};
        if (!_.isEmpty(this.helpDocs)) {
            this.showHelpCenter = true;
            this.rootDocs = this.helpDocs[''];
            this.setPageDocs(this.$state);
            this.$rootScope.$on('$stateChangeSuccess', () => {
                this.setPageDocs(this.$state);
            });
        }

        // this is a temp solution to hide admin option in the dropdown
        // from roles except platform admin, org admin, super user
        this.authService.getCurrentUser().then(resp => {
            let isSuperuser = resp.isSuperuser;
            this.currentUgrPromise = this.authService.fetchUserRoles().then(res => {
                this.platformId = res.find(r => r.groupType === 'PLATFORM').groupId;
                let isPlatOrgAdmin = res.filter((ugr) => {
                    return ugr.groupType === 'PLATFORM' &&
                        ugr.groupRole === 'ADMIN' &&
                        ugr.membershipStatus === 'APPROVED' &&
                        ugr.isActive;
                }).length;
                this.showAdmin = isPlatOrgAdmin || isSuperuser;
            });
        });
    }

    onHelpVideoClicked(isRootVid, doc) {
        this.onVideoClose();
        this.$timeout(() => {
            this.isRootVid = isRootVid;
            this.videoUrl = this.$sce.trustAsResourceUrl(doc.link + '?rel=0&controls=0&showinfo=0');
            this.initPlyr();
        }, 100);
    }

    initPlyr() {
        this.$timeout(() => {
            this.plyr = new Plyr('#player');
            this.plyr.on('enterfullscreen', this.onEnterFullscreen.bind(this));
            this.plyr.on('exitfullscreen', this.onExitFullscreen.bind(this));
            this.showVideo = true;
        }, 100);
    }

    onEnterFullscreen() {
        const plyrContainer = angular.element(this.$document[0].querySelector('#player'));
        plyrContainer.toggleClass('full-screen', true);
        this.isFullScreenMode = true;
        this.$scope.$evalAsync();
    }

    onExitFullscreen() {
        const plyrContainer = angular.element(this.$document[0].querySelector('#player'));
        plyrContainer.toggleClass('full-screen', false);
        this.isFullScreenMode = false;
        this.$scope.$evalAsync();
    }

    onVideoClose(e = {}) {
        if (!_.isEmpty(e)) {
            e.stopPropagation();
        }
        delete this.videoUrl;
        this.showVideo = false;
        this.showMini = false;
    }

    onVideoCollapse() {
        this.showVideo = false;
        this.$timeout(() => {
            this.showMini = true;
            this.isMiniVideoHover = false;
        }, 300);
    }

    onMiniVideoHover(status) {
        this.isMiniVideoHover = status;
    }

    onMiniVideoClick() {
        if (this.videoUrl) {
            this.showVideo = true;
            this.showMini = false;
        }
    }

    setPageDocs(state) {
        if (state.current && state.current.name && state.current.name.length) {
            this.pageDocs = this.helpDocs[state.current.name.replace('.', '_')];
            if (!this.isRootVid) {
                this.onVideoClose();
            }
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

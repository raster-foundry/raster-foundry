import assetLogo from '../../../assets/images/logo-raster-foundry.png';

export default class NavBarController {
    constructor( // eslint-disable-line max-params
        $log, $state, $uibModal, store, $scope, APP_CONFIG, authService
    ) {
        'ngInject';

        this.$log = $log;
        this.$state = $state;
        this.$uibModal = $uibModal;
        this.store = store;
        this.authService = authService;

        if (APP_CONFIG.error) {
            this.loadError = true;
        }

        this.optionsOpen = false;
        this.assetLogo = assetLogo;
    }

    gotoEditor() {
        if (!this.$state.$current.name.includes('editor')) {
            if (this.activeModal) {
                this.activeModal.dismiss();
            }

            this.activeModal = this.$uibModal.open({
                component: 'rfSelectProjectModal'
            });

            this.activeModal.result.then(p => {
                this.$state.go('editor.project.color.scenes', {projectid: p.id});
            });
        }
        return this.activeModal;
    }

    gotoLab() {
        if (!this.$state.$current.name.includes('lab')) {
            if (this.activeModal) {
                this.activeModal.dismiss();
            }

            this.activeModal = this.$uibModal.open({
                component: 'rfSelectToolModal'
            });

            this.activeModal.result.then(t => {
                this.$state.go('lab.run', {toolid: t.id});
            });
        }

        return this.activeModal;
    }

    signin() {
        this.authService.login();
    }

    logout() {
        this.authService.logout();
    }
}

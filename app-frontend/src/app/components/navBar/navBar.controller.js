import assetLogo from '../../../assets/images/logo-raster-foundry.png';

export default class NavBarController {
    constructor( // eslint-disable-line max-params
        $log, $state, $scope, $uibModal, APP_CONFIG, authService, localStorage, projectService
    ) {
        'ngInject';
        if (APP_CONFIG.error) {
            this.loadError = true;
        }

        this.$log = $log;
        this.$state = $state;
        this.$scope = $scope;
        this.$uibModal = $uibModal;
        this.localStorage = localStorage;
        this.authService = authService;
        this.projectService = projectService;
    }

    $onInit() {
        this.optionsOpen = false;
        this.assetLogo = assetLogo;
    }

    signin() {
        this.authService.login();
    }

    logout() {
        this.authService.logout();
    }

    selectProjectModal() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfSelectProjectModal',
            resolve: {
                project: () => this.projectService.currentProject
            }
        });

        this.activeModal.result.then(p => {
            this.$state.go(this.$state.$current, { projectid: p.id });
        });

        return this.activeModal;
    }
}

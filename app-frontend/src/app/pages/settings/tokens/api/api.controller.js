class ApiTokensController {
    constructor($log, $uibModal, $stateParams, $state, tokenService, authService, APP_CONFIG) {
        'ngInject';
        this.$log = $log;

        this.tokenService = tokenService;
        this.authService = authService;
        this.$uibModal = $uibModal;
        this.$stateParams = $stateParams;
        this.APP_CONFIG = APP_CONFIG;
        this.$state = $state;
        this.loading = true;

        this.fetchTokens();
    }

    $onInit() {
        if (this.$stateParams.code) {
            this.tokenService
                .createApiToken(this.$stateParams.code)
                .then((authResult) => {
                    this.$uibModal.open({
                        component: 'rfRefreshTokenModal',
                        resolve: {
                            refreshToken: () => authResult.refresh_token,
                            name: () => 'Refresh Token'
                        }
                    });
                });
            this.$state.go('.', {code: null, state: null}, {notify: false});
        }
    }

    fetchTokens() {
        this.loading = true;
        this.tokenService.queryApiTokens().then(
            (tokens) => {
                delete this.error;
                this.tokens = tokens;
                this.loading = false;
            },
            (error) => {
                this.error = error;
                this.loading = false;
            });
    }

    createToken(name) {
        this.authService.createRefreshToken(name).then((authResult) => {
            this.$uibModal.open({
                component: 'rfRefreshTokenModal',
                resolve: {
                    refreshToken: () => authResult.refreshToken,
                    name: () => this.lastTokenName
                }
            });
            delete this.newTokenName;
            this.fetchTokens();
        }, (error) => {
            this.$log.debug('error while creating refresh token', error);
            this.fetchTokens();
        });
    }

    deleteToken(token) {
        let id = token.id;
        let deleteModal = this.$uibModal.open({
            component: 'rfConfirmationModal',
            resolve: {
                title: () => 'Delete refresh token?',
                content: () => 'Deleting this refresh token will make any ' +
                    'further requests with it fail',
                confirmText: () => 'Delete Refresh Token',
                cancelText: () => 'Cancel'
            }
        });
        deleteModal.result.then(
            () => {
                this.tokenService.deleteApiToken({id: id}).then(
                    () => {
                        this.fetchTokens();
                    },
                    (err) => {
                        this.$log.debug('error deleting refresh token', err);
                        this.fetchTokens();
                    }
                );
            });
    }
}

export default ApiTokensController;

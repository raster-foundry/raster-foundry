class ApiTokensController {
    constructor($log, $uibModal, tokenService, authService) {
        'ngInject';
        this.$log = $log;

        this.tokenService = tokenService;
        this.authService = authService;
        this.$uibModal = $uibModal;
        this.loading = true;

        this.fetchTokens();
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

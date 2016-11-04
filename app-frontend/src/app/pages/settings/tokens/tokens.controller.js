class TokensController {
    constructor($log, tokenService, authService, $uibModal) {
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
        this.tokenService.query().then(
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
        this.$log.log('trying to create new refresh token with name', name);
        this.authService.createRefreshToken(name).then((authResult) => {
            this.$uibModal.open({
                component: 'rfRefreshTokenModal',
                resolve: {
                    refreshToken: () => authResult.refreshToken,
                    name: () => this.lastTokenName
                }
            });
            this.fetchTokens();
        }, (error) => {
            this.$log.log('error while creating refresh token', error);
            this.fetchTokens();
        });
    }

    deleteToken(id) {
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
                this.tokenService.delete({id: id}).then(
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

export default TokensController;

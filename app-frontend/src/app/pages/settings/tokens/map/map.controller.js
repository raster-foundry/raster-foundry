class MapTokensController {
    constructor($log, $q, modalService, tokenService, authService) {
        'ngInject';
        this.$log = $log;
        this.$q = $q;

        this.tokenService = tokenService;
        this.authService = authService;
        this.modalService = modalService;
        this.loading = true;

        this.fetchTokens();
    }

    fetchTokens() {
        this.loading = true;
        let profile = this.authService.getProfile();
        if (profile) {
            this.tokenService.queryMapTokens({user: profile.sub}).then(
                (paginatedResponse) => {
                    delete this.error;
                    this.tokens = paginatedResponse.results;
                    this.loading = false;
                },
                (error) => {
                    this.error = error;
                    this.loading = false;
                });
        } else {
            // TODO Toast this
            this.$log.debug('Unable to fetch tokens while user is not logged in');
        }
    }

    deleteToken(token) {
        const modal = this.modalService.open({
            component: 'rfConfirmationModal',
            resolve: {
                title: () => 'Delete map token?',
                content: () => 'Deleting this map token will make any ' +
                    'further requests with it fail',
                confirmText: () => 'Delete Map Token',
                cancelText: () => 'Cancel'
            }
        });

        modal.result.then(() => {
            this.tokenService.deleteMapToken({id: token.id}).then(
                () => {
                    this.fetchTokens();
                },
                (err) => {
                    this.$log.debug('error deleting map token', err);
                    this.fetchTokens();
                }
            );
        });
    }

    updateToken(token, name) {
        let newToken = Object.assign({}, token, {name: name});
        this.tokenService.updateMapToken(newToken).then(() => {
            // TODO: Toast this
            this.fetchTokens();
        }, (err) => {
            // TODO: Toast this
            this.$log.debug('error updating token', err);
            this.fetchTokens();
        });
    }
}

export default MapTokensController;

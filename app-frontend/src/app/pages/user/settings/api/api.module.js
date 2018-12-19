const ApiTokensModule = angular.module('pages.settings.tokens.api', []);

class ApiTokensController {
    constructor(
        $scope, $log, $state, $stateParams, $timeout,
        modalService, tokenService, authService, APP_CONFIG
    ) {
        'ngInject';
        $scope.autoInject(this, arguments);
        this.loading = true;

        this.fetchTokens();
    }

    $onInit() {
        this.test = 'test';
        if (this.$stateParams.code) {
            this.tokenService
                .createApiToken(this.$stateParams.code)
                .then((authResult) => {
                    this.modalService.open({
                        component: 'rfRefreshTokenModal',
                        resolve: {
                            refreshToken: () => authResult.refresh_token,
                            name: () => 'Refresh Token'
                        }
                    }).result.catch(() => {});
                }, (error) => {
                    this.tokenCreateError = true;
                    this.$log.error(error.data);
                    this.$timeout(() => {
                        this.tokenCreateError = false;
                    }, 10000);
                });
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
            this.modalService.open({
                component: 'rfRefreshTokenModal',
                resolve: {
                    refreshToken: () => authResult.refreshToken,
                    name: () => this.lastTokenName
                }
            }).result.catch(() => {});
            delete this.newTokenName;
            this.fetchTokens();
        }, (error) => {
            this.$log.debug('error while creating refresh token', error);
            this.fetchTokens();
        });
    }

    deleteToken(token) {
        let id = token.id;

        const modal = this.modalService.open({
            component: 'rfFeedbackModal',
            resolve: {
                title: () => 'Delete refresh token?',
                subtitle: () =>
                    'Deleting this refresh token will make '
                    + 'any further requests with it fail',
                content: () =>
                    '<h2>Do you wish to continue?</h2>'
                    + '<p>This is a permanent action.</p>',
                /* feedbackIconType : default, success, danger, warning */
                feedbackIconType: () => 'danger',
                feedbackIcon: () => 'icon-warning',
                feedbackBtnType: () => 'btn-danger',
                feedbackBtnText: () => 'Delete refresh token',
                cancelText: () => 'Cancel'
            }
        });

        modal.result.then(() => {
            this.tokenService.deleteApiToken({id: id}).then(
                () => {
                    this.fetchTokens();
                },
                (err) => {
                    this.$log.debug('error deleting refresh token', err);
                    this.fetchTokens();
                }
            );
        }).catch(() => {});
    }
}

ApiTokensModule.controller('ApiTokensController', ApiTokensController);

export default ApiTokensModule;

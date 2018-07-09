class PrivacyController {
    constructor($window, authService, userService) {
        'ngInject';
        this.$window = $window;
        this.authService = authService;
        this.userService = userService;
    }

    $onInit() {
        this.authService.getCurrentUser().then((user) => {
            this.user = user;
        });
    }

    updateUserPrivacy(visibility) {
        this.userService.updateOwnUser(Object.assign({}, this.user, {visibility})).then(res => {
            this.user = res;
        }, () => {
            this.$window.alert('Privacy cannot be set at this time. Please try again later.');
        });
    }
}

const PrivacyModule = angular.module('pages.settings.privacy', []);

PrivacyModule.controller('PrivacyController', PrivacyController);

export default PrivacyModule;

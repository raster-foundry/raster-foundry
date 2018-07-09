const SUBSCRIPTIONS = ['AOI project update', 'scene ingest status', 'scene export status'];

class NotificationController {
    constructor($window, authService, userService) {
        'ngInject';
        this.$window = $window;
        this.authService = authService;
        this.userService = userService;
        this.subscriptions = SUBSCRIPTIONS;
    }

    $onInit() {
        this.authService.getCurrentUser().then((user) => {
            this.user = user;
        });
        this.subscriptionsString = this.subscriptions.join(', ');
    }

    updateUserEmailNotification() {
        let emailNotifications = !this.user.emailNotifications;
        this.userService.updateOwnUser(Object.assign(
            {},
            this.user,
            {emailNotifications}
        )).then(res => {
            this.user = res;
        }, () => {
            this.$window.alert(
                `Email notification preference cannot be changed at this time.
                Please try again later.`);
        });
    }
}

const NotificationModule = angular.module('pages.settings.notification', []);

NotificationModule.controller('NotificationController', NotificationController);

export default NotificationModule;

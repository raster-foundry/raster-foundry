/* globals _ */

const SUBSCRIPTIONS = ['AOI project update', 'scene ingest status', 'scene export status'];

class NotificationController {
    constructor($scope, $window, authService, userService) {
        'ngInject';
        $scope.autoInject(this, arguments);
        this.subscriptions = SUBSCRIPTIONS;
    }

    $onInit() {
        this.authService.getCurrentUser().then((user) => {
            this.user = user;
            this.userBuffer = _.cloneDeep(this.user);
        });
        this.subscriptionsString = this.subscriptions.join(', ');
    }

    updateUserEmailNotification(emailType = '') {
        if (emailType === 'login') {
            this.userBuffer.emailNotifications = true;
            this.userBuffer.personalInfo.emailNotifications = false;
        } else if (emailType === 'personal') {
            this.userBuffer.personalInfo.emailNotifications = true;
            this.userBuffer.emailNotifications = false;
        } else {
            this.userBuffer.emailNotifications = false;
            this.userBuffer.personalInfo.emailNotifications = false;
        }
        this.userService.updateOwnUser(this.userBuffer).then(res => {
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

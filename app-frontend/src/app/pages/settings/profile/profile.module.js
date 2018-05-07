/* globals process */
class ProfileController {
    constructor($log, localStorage, authService, $window, userService) {
        'ngInject';
        this.$log = $log;
        this.authService = authService;
        this.$window = $window;
        this.userService = userService;
        this.localStorage = localStorage;

        this.profile = localStorage.get('profile');
    }

    $onInit() {
        this.env = process.env.NODE_ENV;
    }

    updateGoogleProfile() {
        this.$window.open('https://aboutme.google.com/', '_blank');
    }

    updateUserMetadata() {
        this.userService.updateUserMetadata(this.profile.user_metadata)
            .then((profile) => {
                this.$log.debug('Profile updated!');
                this.profile = profile;
            }, (err) => {
                this.$log.debug('Error updating profile:', err);
                this.profile = this.localStorage.get('profile');
            });
    }
}

const ProfileModule = angular.module('pages.settings.profile', []);

ProfileModule.controller('ProfileController', ProfileController);

export default ProfileModule;

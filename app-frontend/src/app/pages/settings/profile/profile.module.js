/* globals process */

/* eslint-disable */
const providers = [
    {
        link: 'https://support.google.com/mail/answer/35529?hl=en&co=GENIE.Platform',
        provider: 'google-oauth2',
        name: 'Google'
    },
    {
        link: 'https://help.twitter.com/en/managing-your-account/common-issues-when-uploading-profile-photo',
        provider: 'twitter',
        name: 'Twitter'
    },
    {
        link: 'https://www.facebook.com/help/163248423739693?helpref=faq_content',
        provider: 'facebook',
        name: 'Facebook'
    },
    {
        link: 'https://help.github.com/articles/setting-your-profile-picture/',
        provider: 'github',
        name: 'GitHub'
    },
    {
        link: 'https://auth0.com/docs/user-profile/user-picture',
        provider: 'auth0',
        name: 'Auth0'
    }
];

const defaultProvider = {
    link: 'https://en.gravatar.com/',
    provider: 'gravatar',
    name: 'Gravatar'
};
/* eslint-enable */

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
        this.providers = providers;
        this.defaultProvider = defaultProvider;
        this.getCurrentUser();
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

    getCurrentUser() {
        this.authService.getCurrentUser().then(resp => {
            this.provider = this.providers.find(l => {
                return resp.id.includes(l.provider);
            }) || this.defaultProvider;
        });
    }
}

const ProfileModule = angular.module('pages.settings.profile', []);

ProfileModule.controller('ProfileController', ProfileController);

export default ProfileModule;

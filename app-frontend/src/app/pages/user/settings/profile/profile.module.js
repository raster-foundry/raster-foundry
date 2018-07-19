/* globals process, _ */

/* eslint-disable */
const providers = [
    {
        profilePictureLink: 'https://support.google.com/mail/answer/35529?hl=en&co=GENIE.Platform',
        profileSettingsLink: 'https://aboutme.google.com/',
        provider: 'google-oauth2',
        name: 'Google'
    },
    {
        profilePictureLink: 'https://help.twitter.com/en/managing-your-account/common-issues-when-uploading-profile-photo',
        profileSettingsLink: 'https://twitter.com/settings/profile',
        provider: 'twitter',
        name: 'Twitter'
    },
    {
        profilePictureLink: 'https://www.facebook.com/help/163248423739693?helpref=faq_content',
        profileSettingsLink: 'https://www.facebook.com/settings',
        provider: 'facebook',
        name: 'Facebook'
    },
    {
        profilePictureLink: 'https://help.github.com/articles/setting-your-profile-picture/',
        profileSettingsLink: 'https://github.com/settings/profile',
        provider: 'github',
        name: 'GitHub'
    },
    {
        profilePictureLink: 'https://en.gravatar.com/support/activating-your-account/',
        provider: 'auth0',
        name: 'Auth0'
    }
];

const defaultProvider = {
    profilePictureLink: 'https://en.gravatar.com/support/activating-your-account/',
    provider: 'gravatar',
    name: 'Gravatar'
};

const userOrgTypes = ['COMMERCIAL', 'GOVERNMENT', 'NON-PROFIT', 'ACADEMIC', 'MILITARY', 'OTHER'];
/* eslint-enable */

class ProfileController {
    constructor(
        $scope, $log, $window, $timeout,
        localStorage, authService, userService
    ) {
        'ngInject';
        $scope.autoInject(this, arguments);
        this.profile = localStorage.get('profile');
    }

    $onInit() {
        this.env = process.env.NODE_ENV;
        this.providers = providers;
        this.defaultProvider = defaultProvider;
        this.userOrgTypes = userOrgTypes;
        this.getCurrentUser();
    }

    updateGoogleProfile() {
        this.$window.open(this.provider.profileSettingsLink, '_blank');
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
            this.currentUser = resp;
            this.currentUserBuffer = _.cloneDeep(this.currentUser);
            this.provider = this.providers.find(provider => {
                return resp.id.includes(provider.provider);
            }) || this.defaultProvider;
            this.showProfileSettingsButton = this.isThirdParty();
        });
    }

    isThirdParty() {
        return this.provider.provider === 'google-oauth2' ||
            this.provider.provider === 'twitter' ||
            this.provider.provider === 'facebook' ||
            this.provider.provider === 'github';
    }

    getButtonText() {
        if (this.saved) {
            return 'Saved';
        }
        if (this.error) {
            return 'Error, retry?';
        }
        return 'Save';
    }

    onPersonalInfoSubmit() {
        this.saved = false;
        this.isSendingReq = true;
        this.userService.updateOwnUser(this.currentUserBuffer).then(res => {
            this.saved = true;
            this.$timeout(() => {
                this.saved = false;
                this.isSendingReq = false;
            }, 1000);
            this.currentUser = res;
            delete this.error;
        }, (err) => {
            this.error = err;
            this.isSendingReq = false;
        });
    }
}

const ProfileModule = angular.module('pages.settings.profile', []);

ProfileModule.controller('ProfileController', ProfileController);

export default ProfileModule;

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
        let providerMap = {
            'google-oauth2': 'Google',
            auth0: 'Gravatar',
            facebook: 'Facebook',
            twitter: 'Twitter'
        };
        this.provider = this.profile.identities[0].provider;
        this.providerName = providerMap[this.provider];

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

export default ProfileController;

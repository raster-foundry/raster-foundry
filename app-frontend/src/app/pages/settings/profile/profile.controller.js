class ProfileController {
    constructor($log, auth, store) {
        'ngInject';
        const self = this;
        self.$log = $log;
        self.auth = auth;

        $log.debug('ProfileController initialized');
        this.profile = store.get('profile');
    }
}

export default ProfileController;

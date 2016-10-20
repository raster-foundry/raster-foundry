class ProfileController {
    constructor($log, store) {
        'ngInject';
        const self = this;
        self.$log = $log;

        $log.debug('ProfileController initialized');
        this.profile = store.get('profile');
    }
}

export default ProfileController;

class ProfileController {
    constructor($log, store) {
        'ngInject';
        this.$log = $log;

        this.profile = store.get('profile');
    }
}

export default ProfileController;

class SettingsController {
    constructor($log, auth) {
        'ngInject';
        const self = this;
        self.$log = $log;
        self.auth = auth;

        $log.debug('SettingsController initialized');
    }
}

export default SettingsController;

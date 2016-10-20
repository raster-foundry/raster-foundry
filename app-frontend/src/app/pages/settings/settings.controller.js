class SettingsController {
    constructor($log) {
        'ngInject';
        const self = this;
        self.$log = $log;

        $log.debug('SettingsController initialized');
    }
}

export default SettingsController;

class BucketsController {
    constructor($log, auth) {
        'ngInject';
        const self = this;
        self.$log = $log;
        self.auth = auth;

        $log.debug('BucketsController initialized');
    }
}

export default BucketsController;

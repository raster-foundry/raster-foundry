class BucketsController {
    constructor($log) {
        'ngInject';
        const self = this;
        self.$log = $log;

        $log.debug('BucketsController initialized');
    }
}

export default BucketsController;

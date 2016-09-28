class LibraryController {
    constructor($log, auth, $state) {
        'ngInject';
        const self = this;
        self.$log = $log;
        self.auth = auth;

        $log.debug('LibraryController initialized');
        // container view, so right panel contains nothing unless it's in a sub-route
        $state.go('library.scenes');
    }
}

export default LibraryController;

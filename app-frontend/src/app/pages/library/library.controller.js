class LibraryController {
    constructor($log, auth, $state) {
        'ngInject';
        const self = this;
        self.$log = $log;
        self.auth = auth;
        self.$state = $state;

        // container view, so right panel contains nothing unless it's in a sub-route
    }
}

export default LibraryController;

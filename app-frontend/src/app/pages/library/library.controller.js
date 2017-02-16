class LibraryController {
    constructor($log, $state) {
        'ngInject';
        this.$log = $log;
        this.$state = $state;

        // container view, so right panel contains nothing unless it's in a sub-route
    }
}

export default LibraryController;

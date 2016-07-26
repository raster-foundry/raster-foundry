'use strict';

class MainController {
    constructor($log, $state) {
        'ngInject';

        $log.debug('Main controller initialized. Redirecting to /login.');
        $state.go('login');
    }
}

export default MainController;

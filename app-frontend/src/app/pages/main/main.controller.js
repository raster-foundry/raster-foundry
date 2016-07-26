'use strict';

function MainController($log, $state) {
    'ngInject';

    $log.debug('Hello from main controller!');
    $state.go('login');
}

export default MainController;

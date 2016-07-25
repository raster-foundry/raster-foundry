'use strict';

function config($logProvider, $compileProvider) {
	  'ngInject';

    // Enable log
    $logProvider.debugEnabled(true);
    $compileProvider.debugInfoEnabled(false);
}

export default config;

'use strict';
/* global document */
import angular from 'angular';


// index.html page to dist folder
import '!!file-loader?name=[name].[ext]!../favicon.ico';

// main App module
import './index.module';

import '../assets/styles/sass/main.scss';

angular.element(document).ready(function () {
    angular.bootstrap(document, ['rasterFoundry'], {
        strictDi: true
    });
});

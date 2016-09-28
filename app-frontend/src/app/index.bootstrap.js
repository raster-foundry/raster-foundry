/* global document */

// index.html page to dist folder
import '!!file-loader?name=[name].[ext]!../favicon.ico';

// main App module
import './index.module';

import '../assets/styles/sass/app.scss';

angular.element(document).ready(function () {
    angular.bootstrap(document, ['rasterFoundry'], {
        strictDi: true
    });
});

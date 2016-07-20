import angular from 'angular';
require('./library.scss');
import leafletMap from '../components/leafletMap/leafletMap';


let library = {
    template: require('./library.html'),
    controller: 'LibraryCtrl'
};

class LibraryCtrl {
    constructor() {
    }
}

const MODULE_NAME = 'app.library';
angular.module(MODULE_NAME, [
    leafletMap
])
    .component('rfLibrary', library)
    .controller('LibraryCtrl', LibraryCtrl);

export default MODULE_NAME;

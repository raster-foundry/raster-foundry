import LibraryController from './library.controller.js';
require('./../../../assets/font/fontello/css/fontello.css');

const LibraryModule = angular.module('pages.library', ['components.mapContainer']);

LibraryModule.controller('LibraryController', LibraryController);

export default LibraryModule;

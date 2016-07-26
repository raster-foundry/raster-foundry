import LibraryController from './library.controller.js';
require('./../../../assets/font/fontello/css/raster-foundry.css');

const LibraryModule = angular.module('pages.library', ['components.leafletMap']);

LibraryModule.controller('LibraryController', LibraryController);

export default LibraryModule;

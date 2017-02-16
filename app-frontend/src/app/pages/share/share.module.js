import angular from 'angular';
import ShareController from './share.controller.js';

const ShareModule = angular.module('pages.share', []);

ShareModule.controller('ShareController', ShareController);

export default ShareModule;

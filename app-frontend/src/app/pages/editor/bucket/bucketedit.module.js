import angular from 'angular';
import ngInfiniteScroll from 'ng-infinite-scroll';
import modal from 'angular-ui-bootstrap/src/modal';
import BucketEditController from './bucketedit.controller.js';

const BucketEditModule = angular.module('pages.editor.bucket', [
    'components.leafletMap', ngInfiniteScroll, modal,
    'components.colorCorrectPane', 'components.channelHistogram'
]);

BucketEditModule.controller('BucketEditController', BucketEditController);

export default BucketEditModule;

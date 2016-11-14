import angular from 'angular';
import dropdown from 'angular-ui-bootstrap/src/dropdown';
import modal from 'angular-ui-bootstrap/src/modal';
import BucketEditController from './bucketedit.controller.js';

const BucketEditModule = angular.module('pages.editor.bucket', [
    'components.leafletMap', modal, dropdown
]);

BucketEditModule.controller('BucketEditController', BucketEditController);

export default BucketEditModule;

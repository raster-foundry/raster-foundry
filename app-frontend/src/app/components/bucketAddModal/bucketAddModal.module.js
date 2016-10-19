require('../../../assets/font/fontello/css/fontello.css');

import angular from 'angular';
import BucketAddModalComponent from './bucketAddModal.component.js';
import BucketAddModalController from './bucketAddModal.controller.js';

const BucketAddModalModule = angular.module('components.bucketAddModal', []);

BucketAddModalModule.controller('BucketAddModalController', BucketAddModalController);
BucketAddModalModule.component('rfBucketAddModal', BucketAddModalComponent);

export default BucketAddModalModule;

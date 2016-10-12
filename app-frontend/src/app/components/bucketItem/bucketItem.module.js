import angular from 'angular';
import BucketItemComponent from './bucketItem.component.js';
import BucketItemController from './bucketItem.controller.js';
require('../../../assets/font/fontello/css/fontello.css');

const BucketItemModule = angular.module('components.bucketItem', []);

BucketItemModule.controller('BucketItemController', BucketItemController);
BucketItemModule.component('rfBucketItem', BucketItemComponent);

export default BucketItemModule;

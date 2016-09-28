import BucketsController from './buckets.controller.js';

const BucketsModule = angular.module('pages.library.buckets', []);

BucketsModule.controller('BucketsController', BucketsController);

export default BucketsModule;

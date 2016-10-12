import pagination from 'angular-ui-bootstrap/src/pagination';

import BucketsDetailController from './detail.controller.js';

const BucketsDetailModule = angular.module('pages.library.buckets.detail', [pagination]);

BucketsDetailModule.controller('BucketsDetailController', BucketsDetailController);

export default BucketsDetailModule;

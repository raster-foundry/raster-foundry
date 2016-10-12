import pagination from 'angular-ui-bootstrap/src/pagination';
import modal from 'angular-ui-bootstrap/src/modal';

import BucketsListController from './list.controller.js';

const BucketsListModule = angular.module('pages.library.buckets.list', [pagination, modal]);

BucketsListModule.controller('BucketsListController', BucketsListController);

export default BucketsListModule;

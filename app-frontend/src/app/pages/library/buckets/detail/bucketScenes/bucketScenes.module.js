import pagination from 'angular-ui-bootstrap/src/pagination';

import BucketScenesController from './bucketScenes.controller.js';

const BucketScenesModule = angular.module('pages.library.buckets.detail.scenes',
                                           [pagination]);

BucketScenesModule.controller('BucketScenesController', BucketScenesController);

export default BucketScenesModule;

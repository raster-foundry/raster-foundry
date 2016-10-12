// Component code
import bucketItemTpl from './bucketItem.html';

const rfBucketItem = {
    templateUrl: bucketItemTpl,
    controller: 'BucketItemController',
    bindings: {
        bucket: '<',
        selected: '&',
        onSelect: '&'
    }
};

export default rfBucketItem;

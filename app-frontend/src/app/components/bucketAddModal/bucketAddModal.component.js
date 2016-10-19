// Component code
import bucketAddModalTpl from './bucketAddModal.html';

const BucketAddModal = {
    templateUrl: bucketAddModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'BucketAddModalController'
};

export default BucketAddModal;

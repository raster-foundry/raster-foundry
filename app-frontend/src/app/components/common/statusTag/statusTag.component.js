import statusTagTpl from './statusTag.html';

const rfStatusTag = {
    templateUrl: statusTagTpl,
    controller: 'StatusTagController',
    bindings: {
        entityType: '@',
        status: '<'
    }
};

export default rfStatusTag;

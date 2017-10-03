import constantNodeTpl from './constantNode.html';

export default {
    templateUrl: constantNodeTpl,
    controller: 'ConstantNodeController',
    bindings: {
        node: '<',
        model: '<',
        onChange: '&'
    }
};

import constantNodeTpl from './constantNode.html';

export default {
    templateUrl: constantNodeTpl,
    controller: 'ConstantNodeController',
    bindings: {
        model: '<',
        onChange: '&'
    }
};

import inputNodeTpl from './inputNode.html';

export default {
    templateUrl: inputNodeTpl,
    controller: 'InputNodeController',
    bindings: {
        model: '<',
        onChange: '&'
    }
};

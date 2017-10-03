import inputNodeTpl from './inputNode.html';

export default {
    templateUrl: inputNodeTpl,
    controller: 'InputNodeController',
    bindings: {
        node: '<',
        tick: '<',
        model: '<',
        onChange: '&'
    }
};

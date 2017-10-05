import classifyNodeTpl from './classifyNode.html';

export default {
    templateUrl: classifyNodeTpl,
    controller: 'ClassifyNodeController',
    bindings: {
        node: '<',
        model: '<',
        child: '<',
        onChange: '&'
    }
};

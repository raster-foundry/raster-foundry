import classifyNodeTpl from './classifyNode.html';

export default {
    templateUrl: classifyNodeTpl,
    controller: 'ClassifyNodeController',
    bindings: {
        model: '<',
        onChange: '&'
    }
};

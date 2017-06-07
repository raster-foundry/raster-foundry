import tokenTpl from './tokenItem.html';

const rfTokenItem = {
    templateUrl: tokenTpl,
    controller: 'TokenItemController',
    bindings: {
        token: '<',
        onDelete: '&',
        onUpdate: '&'
    }
};

export default rfTokenItem;

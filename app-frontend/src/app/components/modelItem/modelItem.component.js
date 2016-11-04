import modelTpl from './modelItem.html';

const rfModelItem = {
    templateUrl: modelTpl,
    controller: 'ModelItemController',
    bindings: {
        modelData: '<'
    }
};

export default rfModelItem;

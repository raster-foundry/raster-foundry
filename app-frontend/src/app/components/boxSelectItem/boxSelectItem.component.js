import boxSelectItemTpl from './boxSelectItem.html';

const rfBoxSelectItem = {
    templateUrl: boxSelectItemTpl,
    controller: 'BoxSelectItemController',
    transclude: true,
    bindings: {
        title: '@',
        isSelected: '<'
    }
};

export default rfBoxSelectItem;

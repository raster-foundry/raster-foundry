import toolTpl from './toolItem.html';

const rfToolItem = {
    templateUrl: toolTpl,
    controller: 'ToolItemController',
    bindings: {
        toolData: '<'
    }
};

export default rfToolItem;

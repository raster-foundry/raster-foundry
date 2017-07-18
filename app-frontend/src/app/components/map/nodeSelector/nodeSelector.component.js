import nodeSelectorTpl from './nodeSelector.html';
const nodeSelector = {
    templateUrl: nodeSelectorTpl,
    controller: 'NodeSelectorController',
    bindings: {
        nodeMap: '<',
        selected: '<',
        position: '<',
        onSelect: '&',
        onClose: '&'
    }
};
export default nodeSelector;

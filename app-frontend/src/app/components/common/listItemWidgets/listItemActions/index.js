import tpl from './index.html';

const component = {
    bindings: {
        actions: '<'
    },
    templateUrl: tpl
};

export default angular
    .module('components.common.listItemWidgets.listItemActions', [])
    .component('rfListItemActions', component)
    .name;

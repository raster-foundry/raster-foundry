import tpl from './index.html';

// class ListItemActionsController {
// }
const component = {
    bindings: {
        actions: '<'
    },
    templateUrl: tpl
    // controller: ListItemActionsController.name
};

export default angular
    .module('components.common.listItemWidgets.listItemActions', [])
    // .controller(ListItemActionsController.name, ListItemActionsController)
    .component('rfListItemActions', component)
    .name;

import tpl from './index.html';

class ListItemActionsController {
    wrapCallback($event, callback) {
        $event.stopPropagation();
        $event.preventDefault();
        this.dropdownOpen = false;
        callback($event);
    }

    dropdownToggle($event) {
        $event.stopPropagation();
        $event.preventDefault();
        this.dropdownOpen = !this.dropdownOpen;
    }
}

const component = {
    bindings: {
        actions: '<'
    },
    controller: ListItemActionsController.name,
    templateUrl: tpl
};

export default angular
    .module('components.common.listItemWidgets.listItemActions', [])
    .controller(ListItemActionsController.name, ListItemActionsController)
    .component('rfListItemActions', component).name;

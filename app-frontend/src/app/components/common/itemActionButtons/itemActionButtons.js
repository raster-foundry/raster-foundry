import angular from 'angular';

import itemActionButtonsTpl from './itemActionButtons.html';

const ItemActionButtonsComponent = {
    templateUrl: itemActionButtonsTpl,
    controller: 'ItemActionButtonsController',
    bindings: {
        actions: '<',
        item: '<'
    }
};

class ItemActionButtonsController {
    onActionClick(event, action) {
        event.stopPropagation();
        if (action.onClick) {
            action.onClick(this.item);
        }
    }

    getActionIcon(action) {
        let classes = {};
        if (action.iconClass) {
            classes[action.iconClass] = true;
        }
        return classes;
    }

    getActionButtonClass(action) {
        let classes = {};
        if (action.buttonClass) {
            classes[action.buttonClass] = true;
        }
        return classes;
    }
}

const ItemActionButtonsModule = angular.module('components.common.itemActionButtons', []);

ItemActionButtonsModule.component('rfItemActionButtons', ItemActionButtonsComponent);
ItemActionButtonsModule.controller('ItemActionButtonsController', ItemActionButtonsController);

export default ItemActionButtonsModule;

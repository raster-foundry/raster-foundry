import tpl from './index.html';

class ListItemSelectorController {
    constructor(uuid4) {
        'ngInject';
        this.uuid4 = uuid4;
    }

    $onInit() {
        if (!this.id) {
            this.id = this.uuid4.generate();
        }
    }

    $onChanges(changes) {
        if (changes.color) {
            const rx = /^#(?:[0-9a-f]{3}){1,2}$/i;
            const color = changes.color.currentValue;
            if (color && color.match(rx)) {
                this.colorValue = color;
            } else {
                this.colorValue = 'black';
            }
        }
    }
}

const component = {
    bindings: {
        id: '<?',
        onSelect: '&?',
        selected: '<',
        disableSelection: '<',
        color: '<'
    },
    controller: ListItemSelectorController.name,
    templateUrl: tpl
};

export default angular.module('components.common.listItemWidgets.listItemSelector', [])
    .component('rfListItemSelector', component)
    .controller(ListItemSelectorController.name, ListItemSelectorController)
    .name;

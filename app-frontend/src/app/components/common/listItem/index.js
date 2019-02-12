import _ from 'lodash';
import tpl from './index.html';

class ListItemController {
    $onInit() {
        const rx = /^#(?:[0-9a-f]{3}){1,2}$/i;
        const color = this.color;
        if (color && color.match(rx)) {
            this.color = color;
        } else {
            this.color = 'black';
        }
    }
}

const component = {
    bindings: {
        id: '<',
        color: '<',
        title: '<',
        subtitle: '<',
        date: '<',
        getImage: '&?',
        onImageClick: '&?',
        selected: '<',
        onSelect: '&?'
    },
    templateUrl: tpl,
    controller: ListItemController.name,
    transclude: true
};

export default angular
    .module('components.common.listItem', [])
    .controller(ListItemController.name, ListItemController)
    .component('rfListItem', component)
    .name;

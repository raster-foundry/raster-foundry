import { isObject } from 'lodash';
import tpl from './index.html';

const component = {
    bindings: {
        statusMap: '<',
        statuses: '<',
        hasCount: '<'
    },
    templateUrl: tpl
};

export default angular
    .module('components.common.listItemWidgets.listItemStatus', [])
    .component('rfListItemStatus', component).name;

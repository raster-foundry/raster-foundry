import _ from 'lodash';
import tpl from './index.html';

const component = {
    bindings: {
        id: '<'
    },
    templateUrl: tpl,
    transclude: {
        selector: '?itemSelector',
        title: 'itemTitle',
        subtitle: '?itemSubtitle',
        date: '?itemDate',
        preview: '?itemPreview',
        actions: '?itemActions',
        statistics: '?itemStatistics'
    }
};

export default angular
    .module('components.common.listItem', [])
    .component('rfListItem', component)
    .name;

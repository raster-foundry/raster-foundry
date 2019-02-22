import tpl from './index.html';
import _ from 'lodash';

class LayerItemController {
    constructor($rootScope, $scope, $state, projectService) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        const rx = /^#(?:[0-9a-f]{3}){1,2}$/i;
        const color = _.get(this.itemInfo, 'colorGroupHex');
        if (color && color.match(rx)) {
            this.color = color;
        } else {
            this.color = 'gray';
        }

        const geometry = _.get(this.itemInfo, 'colorGroupHex');
        this.hasGeom = _.get(this.itemInfo, 'geometry.features.length');
    }
}

const component = {
    bindings: {
        itemInfo: '<',
        itemActions: '<',
        selected: '<',
        onSelect: '&?',
        visible: '<',
        onHide: '&?',
        isAnalysis: '<?',
        isExport: '<?',
        onDownloadExport: '&?'
    },
    templateUrl: tpl,
    controller: LayerItemController.name,
    transclude: true
};

export default angular
    .module('components.projects.layerItem', [])
    .controller(LayerItemController.name, LayerItemController)
    .component('rfLayerItem', component)
    .name;

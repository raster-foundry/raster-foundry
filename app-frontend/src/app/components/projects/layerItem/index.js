import tpl from './index.html';
import _ from 'lodash';

class LayerItemController {
    constructor($rootScope, $scope, $state, projectService) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        const rx = /^#(?:[0-9a-f]{3}){1,2}$/i;
        const color = _.get(this.layer, 'colorGroupHex');
        if (color.match(rx)) {
            this.color = color;
        } else {
            this.color = 'white';
        }

        const geometry = _.get(this.layer, 'colorGroupHex');
        this.hasGeom = _.get(geometry, 'features.length');
    }

    getSceneCount() {
        return this.layerStats ? this.layerStats[this.layer.id] : null;
    }
}

const component = {
    bindings: {
        layer: '<',
        layerStats: '<',
        layerActions: '<',
        subtext: '<',
        selected: '<',
        onSelect: '&',
        visible: '<',
        onHide: '&'
    },
    templateUrl: tpl,
    controller: LayerItemController.name
};

export default angular
    .module('components.projects.layerItem', [])
    .controller(LayerItemController.name, LayerItemController)
    .component('rfLayerItem', component)
    .name;

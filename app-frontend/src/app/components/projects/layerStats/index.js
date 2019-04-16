import tpl from './index.html';
import _ from 'lodash';

class LayerStatsController {
    constructor($rootScope, $scope, $state) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }
}

const component = {
    bindings: {
        sceneCount: '<'
    },
    templateUrl: tpl,
    controller: LayerStatsController.name
};

export default angular
    .module('components.projects.layerStats', [])
    .controller(LayerStatsController.name, LayerStatsController)
    .component('rfLayerStats', component)
    .name;

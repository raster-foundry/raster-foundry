require('angular-ui-tree/source/angular-ui-tree.css');

import angular from 'angular';
import uiTree from 'angular-ui-tree';
import buttons from 'angular-ui-bootstrap/src/buttons';

import MosaicScenesParameters from './mosaicScenesParameters.component.js';
import MosaicScenesParametersController from './mosaicScenesParameters.controller.js';

const MosaicScenesParametersModule = angular.module(
    'components.mosaicScenesParameters', [uiTree, buttons]
);

MosaicScenesParametersModule.component('rfMosaicParams', MosaicScenesParameters);
MosaicScenesParametersModule.controller('MosaicScenesParametersController',
    MosaicScenesParametersController
);

export default MosaicScenesParametersModule;

import angular from 'angular';

import ReclassifyModalComponent from './reclassifyModal.component.js';
import ReclassifyModalController from './reclassifyModal.controller.js';

const ReclassifyModalModule = angular.module('components.tools.reclassifyModal', []);

ReclassifyModalModule.controller('ReclassifyModalController', ReclassifyModalController);
ReclassifyModalModule.component('rfReclassifyModal', ReclassifyModalComponent);

export default ReclassifyModalModule;

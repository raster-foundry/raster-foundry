import angular from 'angular';
import DownloadModalComponent from './downloadModal.component.js';
import DownloadModalController from './downloadModal.controller.js';
require('../../../assets/font/fontello/css/fontello.css');

const DownloadModalModule = angular.module('components.downloadModal', []);

DownloadModalModule.controller(
    'DownloadModalController', DownloadModalController
);
DownloadModalModule.component(
    'rfDownloadModal', DownloadModalComponent
);

export default DownloadModalModule;

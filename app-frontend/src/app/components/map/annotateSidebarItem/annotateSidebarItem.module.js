import angular from 'angular';
import AnnotateSidebarItemComponent from './annotateSidebarItem.component.js';
import AnnotateSidebarItemController from './annotateSidebarItem.controller.js';
require('./annotateSidebarItem.scss');

const AnnotateSidebarItemModule = angular.module('components.map.annotateSidebarItem', []);

AnnotateSidebarItemModule.component('rfAnnotateSidebarItem', AnnotateSidebarItemComponent);
AnnotateSidebarItemModule.controller(
    'AnnotateSidebarItemController',
    AnnotateSidebarItemController
);

export default AnnotateSidebarItemModule;

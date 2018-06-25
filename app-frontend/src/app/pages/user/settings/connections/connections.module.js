import ConnectionsController from './connections.controller.js';

const ConnectionsModule = angular.module('pages.settings.connections', []);

ConnectionsModule.controller('ConnectionsController', ConnectionsController);

export default ConnectionsModule;

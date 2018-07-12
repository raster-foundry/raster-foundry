import angular from 'angular';

class AdminController {
    constructor() { }
}

const AdminModule = angular.module('pages.admin', []);
AdminModule.controller('AdminController', AdminController);

export default AdminModule;

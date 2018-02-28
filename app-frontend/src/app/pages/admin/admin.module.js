import angular from 'angular';

class AdminController {
    constructor() {
        // eslint-disable-next-line
        console.log('Admin Controller');
    }
}

const AdminModule = angular.module('pages.admin', []);
AdminModule.controller('AdminController', AdminController);

export default AdminModule;

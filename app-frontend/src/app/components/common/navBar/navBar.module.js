import angular from 'angular';
import dropdown from 'angular-ui-bootstrap/src/dropdown';
require('../../../../assets/font/icomoon/style.css');
require('plyr/dist/plyr.css');

import NavBarComponent from './navBar.component.js';
import NavBarController from './navBar.controller.js';

const NavBarModule = angular.module('components.common.navBar', [dropdown]);

NavBarModule.component('rfNavBar', NavBarComponent);
NavBarModule.controller('NavBarController', NavBarController);

export default NavBarModule;

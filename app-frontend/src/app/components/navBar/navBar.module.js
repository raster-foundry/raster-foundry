import angular from 'angular';
import dropdown from 'angular-ui-bootstrap/src/dropdown';
require('../../../assets/font/fontello/css/fontello.css');

import NavBarComponent from './navBar.component.js';
import NavBarController from './navBar.controller.js';

const NavBarModule = angular.module('components.navBar', [dropdown]);

NavBarModule.component('rfNavBar', NavBarComponent);
NavBarModule.controller('NavBarController', NavBarController);

export default NavBarModule;

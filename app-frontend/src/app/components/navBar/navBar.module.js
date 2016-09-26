import angular from 'angular';
import dropdown from 'angular-ui-bootstrap/src/dropdown';
import NavBarComponent from './navBar.component.js';
import NavBarController from './navBar.controller.js';
require('../../../assets/font/fontello/css/fontello.css');

const NavBarModule = angular.module('components.navBar', [dropdown]);

NavBarModule.component('rfNavBar', NavBarComponent);
NavBarModule.controller('NavBarController', NavBarController);

export default NavBarModule;

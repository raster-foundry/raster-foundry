import angular from 'angular';
import ColorSchemeDropdownComponent from './colorSchemeDropdown.component.js';
import ColorSchemeDropdownController from './colorSchemeDropdown.controller.js';

const ColorSchemeDropdownModule = angular.module('components.colorSchemeDropdown', []);

ColorSchemeDropdownModule.component(
    'rfColorSchemeDropdown',
    ColorSchemeDropdownComponent
);

ColorSchemeDropdownModule.controller(
    'ColorSchemeDropdownController',
    ColorSchemeDropdownController
);

export default ColorSchemeDropdownModule;

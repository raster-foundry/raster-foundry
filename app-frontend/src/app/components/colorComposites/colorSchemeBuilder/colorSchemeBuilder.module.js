import angular from 'angular';
import ColorSchemeBuilderComponent from './colorSchemeBuilder.component.js';
import ColorSchemeBuilderController from './colorSchemeBuilder.controller.js';
import 'angular-bootstrap-colorpicker';
import 'angular-bootstrap-colorpicker/css/colorpicker.css';

const ColorSchemeBuilderModule = angular.module('components.colorSchemeBuilder', [
    'colorpicker.module'
]);

ColorSchemeBuilderModule.component('rfColorSchemeBuilder', ColorSchemeBuilderComponent);
ColorSchemeBuilderModule.controller('ColorSchemeBuilderController', ColorSchemeBuilderController);

export default ColorSchemeBuilderModule;

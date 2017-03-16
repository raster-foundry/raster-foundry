import ColorCorrectionController from './colorCorrection.controller.js';

const ColorCorrectionModule =
  angular.module('pages.imports.datasources.detail.colorCorrection', []);

ColorCorrectionModule.controller('ColorCorrectionController', ColorCorrectionController);

export default ColorCorrectionModule;

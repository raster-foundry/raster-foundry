import EditorController from './editor.controller.js';

const EditorModule = angular.module('pages.editor', ['components.leafletMap']);

EditorModule.controller('EditorController', EditorController);

export default EditorModule;

import angular from 'angular';
import NewController from './new.controller.js';

const NewExportModule = angular.module('page.projects.edit.exports.new', []);

NewExportModule.controller('NewExportController', NewController);

export default NewExportModule;

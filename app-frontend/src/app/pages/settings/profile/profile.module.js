import ProfileController from './profile.controller.js';
require('./profile.scss');

const ProfileModule = angular.module('pages.settings.profile', []);

ProfileModule.controller('ProfileController', ProfileController);

export default ProfileModule;

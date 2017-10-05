import angular from 'angular';

import SearchComponent from './search.component.js';
import SearchController from './search.controller.js';

const SearchModule = angular.module('components.common.search', []);

SearchModule.component('rfSearch', SearchComponent);
SearchModule.controller('SearchController', SearchController);

export default SearchModule;

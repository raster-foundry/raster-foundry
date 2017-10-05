import searchTpl from './search.html';

const rfSearch = {
    templateUrl: searchTpl,
    controller: 'SearchController',
    bindings: {
        autoFocus: '<',
        placeholder: '@',
        onSearch: '&'
    }
};

export default rfSearch;

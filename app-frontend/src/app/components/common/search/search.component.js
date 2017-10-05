import searchTpl from './search.html';

const rfSearch = {
    templateUrl: searchTpl,
    controller: 'SearchController',
    bindings: {
        placeholder: '@',
        onSearch: '&'
    }
};

export default rfSearch;

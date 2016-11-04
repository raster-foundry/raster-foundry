import searchTpl from './modelSearch.html';

const rfModelSearch = {
    templateUrl: searchTpl,
    controller: 'ModelSearchController',
    bindings: {
        onSearch: '&'
    }
};

export default rfModelSearch;

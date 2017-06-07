import searchTpl from './toolSearch.html';

const rfToolSearch = {
    templateUrl: searchTpl,
    controller: 'ToolSearchController',
    bindings: {
        onSearch: '&'
    }
};

export default rfToolSearch;

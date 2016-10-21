import filterTpl from './filterPane.html';

const rfFilterPane = {
    templateUrl: filterTpl,
    controller: 'FilterPaneController',
    bindings: {
        filters: '='
    }
};

export default rfFilterPane;

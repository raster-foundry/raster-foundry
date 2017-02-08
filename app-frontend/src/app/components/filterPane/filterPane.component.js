import filterTpl from './filterPane.html';

const rfFilterPane = {
    templateUrl: filterTpl,
    controller: 'FilterPaneController',
    bindings: {
        filters: '=',
        opened: '='
    }
};

export default rfFilterPane;

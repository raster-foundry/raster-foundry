import filterTpl from './filterPane.html';

const rfFilterPane = {
    templateUrl: filterTpl,
    controller: 'FilterPaneController',
    bindings: {
        filters: '=',
        onCloseClick: '&'
    }
};

export default rfFilterPane;

import aoiFilterTpl from './aoiFilterPane.html';

const rfAOIFilterPane = {
    templateUrl: aoiFilterTpl,
    controller: 'AOIFilterPaneController',
    bindings: {
        filters: '<',
        opened: '<',
        onFilterChange: '&',
        onCloseFilterPane: '&'
    }
};

export default rfAOIFilterPane;

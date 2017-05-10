import aoiFilterTpl from './aoiFilterPane.html';

const rfAOIFilterPane = {
    templateUrl: aoiFilterTpl,
    controller: 'AOIFilterPaneController',
    bindings: {
        filters: '=',
        opened: '='
    }
};

export default rfAOIFilterPane;

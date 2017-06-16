// Component code
import datasourceItemTpl from './datasourceItem.html';

const rfDatasourceItem = {
    templateUrl: datasourceItemTpl,
    controller: 'DatasourceItemController',
    bindings: {
        datasource: '<',
        selected: '&',
        onSelect: '&'
    }
};

export default rfDatasourceItem;

import datasourceCreateModalTpl from './datasourceCreateModal.html';

const rfDatasourceCreateModal = {
    templateUrl: datasourceCreateModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'DatasourceCreateModalController'
};

export default rfDatasourceCreateModal;

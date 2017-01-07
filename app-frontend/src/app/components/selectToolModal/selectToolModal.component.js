import selectToolModalTpl from './selectToolModal.html';

const SelectToolModalComponent = {
    templateUrl: selectToolModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'SelectToolModalController'
};

export default SelectToolModalComponent;

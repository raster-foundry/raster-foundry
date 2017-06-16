import datePickerModalTpl from './datePickerModal.html';

const rfDatePickerModal = {
    templateUrl: datePickerModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'DatePickerModalController'
};

export default rfDatePickerModal;

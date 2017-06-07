import dateRangePickerModalTpl from './dateRangePickerModal.html';

const rfDateRangePickerModal = {
    templateUrl: dateRangePickerModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'DateRangePickerModalController'
};

export default rfDateRangePickerModal;

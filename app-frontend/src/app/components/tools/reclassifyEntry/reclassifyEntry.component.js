import reclassifyEntryTpl from './reclassifyEntry.html';

const rfReclassifyEntry = {
    templateUrl: reclassifyEntryTpl,
    bindings: {
        classRange: '<',
        classValue: '<',
        entryId: '@',
        onRangeChange: '&',
        onValueChange: '&',
        onValidityChange: '&'
    },
    controller: 'ReclassifyEntryController'
};

export default rfReclassifyEntry;


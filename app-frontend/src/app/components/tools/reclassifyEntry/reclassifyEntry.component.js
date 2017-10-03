import reclassifyEntryTpl from './reclassifyEntry.html';

const rfReclassifyEntry = {
    templateUrl: reclassifyEntryTpl,
    bindings: {
        classRange: '<',
        classValue: '<',
        break: '<',
        entryId: '@',
        onRangeChange: '&',
        onValueChange: '&',
        onBreakChange: '&',
        onValidityChange: '&'
    },
    controller: 'ReclassifyEntryController'
};

export default rfReclassifyEntry;


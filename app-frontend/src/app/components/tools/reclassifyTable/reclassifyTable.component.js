import reclassifyTableTpl from './reclassifyTable.html';

const rfReclassifyTable = {
    templateUrl: reclassifyTableTpl,
    bindings: {
        classifications: '<',
        onClassificationsChange: '&',
        onBreaksChange: '&',
        onAllEntriesValidChange: '&',
        onNoGapsOverlapsChange: '&'
    },
    controller: 'ReclassifyTableController'
};

export default rfReclassifyTable;

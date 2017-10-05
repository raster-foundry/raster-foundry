import reclassifyHistogramTpl from './reclassifyHistogram.html';

const reclassifyHistogram = {
    templateUrl: reclassifyHistogramTpl,
    controller: 'ReclassifyHistogramController',
    bindings: {
        histogram: '<',
        classifications: '<',
        options: '<',
        onMasksChange: '&',
        onBreakpointChange: '&'
    }
};

export default reclassifyHistogram;

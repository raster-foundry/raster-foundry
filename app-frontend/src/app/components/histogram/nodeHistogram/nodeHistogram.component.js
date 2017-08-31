import nodeHistogramTpl from './nodeHistogram.html';

const nodeHistogram = {
    templateUrl: nodeHistogramTpl,
    controller: 'NodeHistogramController',
    bindings: {
        histogram: '<',
        breakpoints: '<',
        masks: '<',
        options: '<',
        onMasksChange: '&',
        onBreakpointChange: '&'
    }
};

export default nodeHistogram;

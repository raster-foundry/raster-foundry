import nodeHistogramTpl from './nodeHistogram.html';

const nodeHistogram = {
    templateUrl: nodeHistogramTpl,
    controller: 'NodeHistogramController',
    bindings: {
        histogram: '<',
        breakpoints: '<',
        options: '<',
        onMasksChange: '&',
        onBreakpointChange: '&'
    }
};

export default nodeHistogram;

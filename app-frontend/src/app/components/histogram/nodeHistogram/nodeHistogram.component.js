import nodeHistogramTpl from './nodeHistogram.html';

const nodeHistogram = {
    templateUrl: nodeHistogramTpl,
    controller: 'NodeHistogramController',
    bindings: {
        astNode: '<',
        histogram: '<',
        breakpoints: '<',
        options: '<',
        onBreakpointChange: '&'
    }
};

export default nodeHistogram;

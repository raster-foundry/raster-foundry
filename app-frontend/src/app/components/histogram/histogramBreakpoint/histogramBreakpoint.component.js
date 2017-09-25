import histogramBreakpointTpl from './histogramBreakpoint.html';

const histogramBreakpoint = {
    templateUrl: histogramBreakpointTpl,
    controller: 'HistogramBreakpointController',
    bindings: {
        color: '<',
        breakpoint: '<',
        range: '<',
        precision: '<',
        options: '<',
        onBreakpointChange: '&'
    }
};

export default histogramBreakpoint;

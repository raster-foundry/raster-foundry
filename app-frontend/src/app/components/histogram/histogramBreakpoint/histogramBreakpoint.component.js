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
        onColorChange: '&',
        onBreakpointChange: '&'
    }
};

export default histogramBreakpoint;

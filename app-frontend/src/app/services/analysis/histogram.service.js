export default (app) => {
    class HistogramService {
        scaleBreakpointsToRange(breakpoints, currentRange, newRange) {
            if (
                breakpoints &&
                    (currentRange.min !== newRange.min ||
                     currentRange.max !== newRange.max) &&
                    newRange.max - newRange.min > 0) {
                breakpoints.forEach((bp) => {
                    let percent = (
                        bp.value - currentRange.min
                    ) / (currentRange.max - currentRange.min);
                    let newVal = percent * (newRange.max - newRange.min) + newRange.min;
                    bp.value = newVal;
                });
            }
        }
    }

    app.service('histogramService', HistogramService);
};

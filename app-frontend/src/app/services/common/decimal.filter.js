export default (app) => {
    // Restrict a number to a desired number of decimal places without trailing zeros
    app.filter('setDecimal', function () {
        'ngInject';
        return function (input, places) {
            if (isNaN(input)) {
                return input;
            }

            let factor = 1;
            if (places > 0) {
                // 2 places : factor = 100 etc
                factor = Math.pow(10, places);
            }
            return Math.round(input * factor) / factor;
        };
    });
};

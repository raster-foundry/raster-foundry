'use strict';
/* globals __dirname module console */
/* eslint no-process-env: 0
 no-console: 0
 */

const _ = require('lodash');
const configs = {
    // global section
    global: require(__dirname + '/config/webpack/global'),
    // config by enviroments
    test: require(__dirname + '/config/webpack/environments/test')
};

let load = function () {
    let ENV = 'test';

    console.log('Current Environment: ', ENV);

    // load config file by environment
    return configs && _.merge(
        configs.global(__dirname),
        configs[ENV](__dirname)
    );
};

let webpackConfig = load();

// Reference: http://karma-runner.github.io/0.12/config/configuration-file.html
module.exports = function karmaConfig(config) {
    config.set({
        frameworks: [
            // Reference: https://github.com/karma-runner/karma-jasmine
            // Set framework to jasmine
            'jasmine'
        ],

        reporters: [
            // Reference: https://github.com/mlex/karma-spec-reporter
            // Set reporter to print detailed results to console
            'progress',

            // Reference: https://github.com/karma-runner/karma-coverage
            // Output code coverage files
            'coverage'
        ],

        files: [
            // Grab all files in the app folder that contain .spec.
            'src/tests.webpack.js'
        ],

        preprocessors: {
            // Reference: http://webpack.github.io/docs/testing.html
            // Reference: https://github.com/webpack/karma-webpack
            // Convert files with webpack and load sourcemaps
            'src/tests.webpack.js': ['webpack', 'sourcemap']
        },

        browsers: [
            // Run tests using PhantomJS
            'PhantomJS'
        ],

        singleRun: true,

        // Configure code coverage reporter
        coverageReporter: {
            dir: 'coverage/',
            reporters: [
                {type: 'text-summary'},
                {type: 'html'}
            ]
        },

        webpack: webpackConfig,

        // Hide webpack build information from output
        webpackMiddleware: {
            noInfo: 'errors-only'
        }
    });
};

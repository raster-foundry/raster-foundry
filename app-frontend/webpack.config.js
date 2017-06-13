'use strict';
/* globals __dirname process module console */
/* eslint no-process-env: 0
          no-console: 0
 */

const fs = require('fs');
const merge = require('webpack-merge');

const configs = {

    // global section
    global: require(__dirname + '/config/webpack/global'),
    overrides: fs.existsSync(__dirname + '/config/webpack/overrides.js') ?
        require(__dirname + '/config/webpack/overrides') : null,

    // config by enviroments
    production: require(__dirname + '/config/webpack/environments/production'),
    development: require(__dirname + '/config/webpack/environments/development'),
    test: require(__dirname + '/config/webpack/environments/test')
};

let load = function () {
    let ENV = process.env.NODE_ENV
            ? process.env.NODE_ENV
            : 'production';

    console.log('Current Environment: ', ENV);

    // load config file by environment
    return configs && merge(
        configs.overrides ? configs.overrides(__dirname) : null,
        configs.global(__dirname),
        configs[ENV](__dirname)
    );
};
module.exports = load();

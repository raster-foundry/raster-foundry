'use strict';
/* globals module */
/* eslint no-process-env: 0
 no-console: 0
 */

let webpack = require('webpack');

module.exports = function (_path) {
    return {
        context: _path,
        debug: true,
        devtool: 'cheap-source-map',
        devServer: {
            contentBase: './dist',
            info: true,
            hot: true,
            inline: true
        },
        plugins: [
            new webpack.HotModuleReplacementPlugin()
        ]
    };
};

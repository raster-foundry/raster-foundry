'use strict';
/* globals module */
/* eslint no-process-env: 0
 no-console: 0
 */

let webpack = require('webpack');
let CleanWebpackPlugin = require('clean-webpack-plugin');

module.exports = function (_path) {
    return {
        context: _path,
        debug: false,
        devtool: 'source-map',
        output: {
            publicPath: '/',
            filename: '[name].[chunkhash].js'
        },
        plugins: [
            new webpack.NoErrorsPlugin(),
            new CleanWebpackPlugin(['dist'], {
                root: _path,
                verbose: true,
                dry: false
            }),
            new webpack.optimize.UglifyJsPlugin({
                minimize: true,
                warnings: false,
                sourceMap: true
            })
        ]
    };
};

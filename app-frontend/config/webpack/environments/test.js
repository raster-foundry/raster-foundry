'use strict';
/* globals module process */
/* eslint no-process-env: 0
 no-console: 0
 */

const webpack = require('webpack');

module.exports = function (_path) {
    return {
        context: _path,
        debug: true,
        devtool: 'cheap-source-map',
        entry: {},
        module: {
            preLoaders: [{
                test: /\.js$/,
                exclude: [
                    /node_modules/,
                    /\.spec\.js$/
                ],
                loader: 'isparta-instrumenter'
            }],
            loaders: [
                {
                    test: /\.scss$/,
                    loader: 'null'
                }, {
                    test: /\.html$/,
                    loader: 'null'
                }
            ]
        },
        plugins: [
            // None of these are used in tests yet, but keep them for parity with
            // development and production configs
            new webpack.DefinePlugin({
                'process.env': {
                    NODE_ENV: JSON.stringify(process.env.NODE_ENV)
                }
            })
        ]
    };
};

'use strict';
/* globals module */
/* eslint no-process-env: 0
 no-console: 0
 */

const path = require('path');
const webpack = require('webpack');
const CleanWebpackPlugin = require('clean-webpack-plugin');
const Manifest = require('manifest-revision-webpack-plugin');

module.exports = function (_path) {
    let rootAssetPath = _path + 'src';

    return {
        context: _path,
        debug: false,
        devtool: 'eval',
        mode: 'production',
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
            new Manifest(path.join(_path + '/dist', 'manifest.json'), {
                rootAssetPath: rootAssetPath,
                ignorePaths: ['.DS_Store']
            }),
            new webpack.DefinePlugin({
                'process.env': {
                    NODE_ENV: JSON.stringify('production')
                }
            })
        ]
    };
};

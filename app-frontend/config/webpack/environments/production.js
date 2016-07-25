'use strict';
let webpack = require('webpack');
let CleanWebpackPlugin = require('clean-webpack-plugin');

module.exports = function (_path) {
    return {
        context: _path,
        debug: false,
        devtool: 'cheap-source-map',
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
            })
        ]
    };
};

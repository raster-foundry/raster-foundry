var webpack = require('webpack'),
    ExtractTextPlugin = require('extract-text-webpack-plugin'),
    CopyWebpackPlugin = require('copy-webpack-plugin'),
    config = require('./webpack.common.config.js'),
    host = process.env.WEBPACK_DEV_SERVER || 'http://localhost:8080';

config.devtool = 'source-map';
config.output.publicPath = '/';
config.filename = '[name].[hash].js';
config.chunkFilename = '[name].[hash].js';
config.module.loaders.push({
    test: /\.js$/,
    loaders: ['ng-annotate', 'babel'],
    exclude: /node_modules/
});
config.module.loaders.push({
    test: /\.scss$/,
    loader: ExtractTextPlugin.extract('style-loader', ['css-loader', 'postcss-loader', 'sass?sourcemap'])
});
config.plugins.push(
    new ExtractTextPlugin('[name].[hash].css'),
    new webpack.NoErrorsPlugin(),
    new webpack.optimize.DedupePlugin(),
    new webpack.optimize.UglifyJsPlugin(),
    new CopyWebpackPlugin([{
        from: './src/public'
    }])
);

module.exports = config;

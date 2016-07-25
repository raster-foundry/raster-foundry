var webpack = require('webpack');
var config = require('./webpack.common.config.js');
var host = process.env.WEBPACK_DEV_SERVER || 'http://localhost:8080';

config.devtool = 'inline-source-map';
config.module.loaders.push({
    test: /\.scss$/,
    exclude: /node_modules/,
    loaders: [
        'style-loader',
        'css-loader?sourceMap',
        'postcss-loader',
        'sass-loader?sourceMap&sourceComments'
    ]
});
// Don't use ng-inject, it messes up source maps in dev.
// Not needed anyways since uglify is only run for prod
config.module.loaders.push({
    test: /\.js$/,
    loaders: ['babel'],
    exclude: /node_modules/
});
config.devServer = {
    contentBase: './src/public',
    stats: 'minimal',
    hot: true
};
config.plugins.push(new webpack.HotModuleReplacementPlugin());
module.exports = config;

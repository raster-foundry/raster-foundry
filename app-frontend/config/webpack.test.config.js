var webpack = require('webpack');
var config = require('./webpack.common.config.js');
var host = process.env.WEBPACK_DEV_SERVER || 'http://localhost:8080';

config.devtool = 'inline-source-map';
config.entry = {};
config.module.preLoaders.unshift({
    test: /\.js$/,
    exclude: [
            /node_modules/,
            /\.spec\.js$/
    ],
    loader: 'isparta-instrumenter'
});
config.module.loaders.push({
    test: /\.js$/,
    loaders: ['babel'],
    exclude: /node_modules/
});
config.module.loaders.push({
    test: /\.scss$/,
    loader: 'null'
});

module.exports = config;

'use strict';
/* globals module process */
/* no-console: 0 */


const webpack = require('webpack');
const port = process.env.RF_PORT_9091 || 9091;
const serverport = process.env.RF_SERVER_PORT || 9100;

module.exports = function (_path) {
    return {
        context: _path,
        debug: true,
        devtool: 'cheap-source-map',
        devServer: {
            contentBase: './dist',
            info: true,
            hot: true,
            inline: true,
            progress: true,
            historyApiFallback: true,
            port: port,
            proxy: {
                '/api/*': 'http://localhost:' + serverport,
                '/feature-flags': 'http://localhost:' + serverport,
                '/config': 'http://localhost:' + serverport
            }
        },
        plugins: [
            new webpack.HotModuleReplacementPlugin(),
            new webpack.DefinePlugin({
                'process.env': {
                    NODE_ENV: JSON.stringify(process.env.NODE_ENV)
                }
            })
        ]
    };
};

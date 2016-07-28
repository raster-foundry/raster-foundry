'use strict';
/* globals module process */
/* no-console: 0
 */


let webpack = require('webpack');
let port = process.env.RF_PORT_9090 || 9090;

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
            'history-api-fallback': true,
            port: port,
            proxy: {
                '*': 'http://localhost:9100'
            }
        },
        plugins: [
            new webpack.HotModuleReplacementPlugin()
        ]
    };
};
